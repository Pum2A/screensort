//! Trusted native bridge: ports and session credentials never enter the webview.

mod packages;
mod paths;
mod processes;
mod smoke;

use regex::Regex;
use serde::Serialize;
use serde_json::Value;
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Mutex,
};
use tauri::{AppHandle, Manager, State};

pub use smoke::launch_startup_check;
pub use smoke::run_smoke;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub phase: String,
    pub backend_ready: bool,
    pub analysis_ready: bool,
    pub analysis_starting: bool,
    pub error: Option<String>,
}

impl Default for Status {
    fn default() -> Self {
        Self {
            phase: "Przygotowujemy aplikację…".into(),
            backend_ready: false,
            analysis_ready: false,
            analysis_starting: false,
            error: None,
        }
    }
}

pub struct Desktop {
    status: Mutex<Status>,
    backend: Mutex<Option<processes::Backend>>,
    owner: tauri::async_runtime::Mutex<Option<processes::Owner>>,
    closing: AtomicBool,
    packages: packages::PackageManager,
}

impl Default for Desktop {
    fn default() -> Self {
        Self {
            status: Mutex::new(Status::default()),
            backend: Mutex::new(None),
            owner: tauri::async_runtime::Mutex::new(None),
            closing: AtomicBool::new(false),
            packages: packages::PackageManager::default(),
        }
    }
}

fn runtime_path(app: &AppHandle) -> Result<std::path::PathBuf, String> {
    if cfg!(debug_assertions) {
        Ok(std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime"))
    } else {
        app.path()
            .resource_dir()
            .map(|path| path.join("runtime"))
            .map_err(|_| "Nie można odnaleźć katalogu instalacji ScreenSort.".into())
    }
}

fn startup_failed(app: &AppHandle, error: String) {
    let desktop = app.state::<Desktop>();
    let mut status = desktop.status.lock().expect("status lock");
    status.backend_ready = false;
    status.analysis_ready = false;
    status.error = Some(error);
}

pub fn launch(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let runtime = match runtime_path(&app) {
            Ok(path) => path,
            Err(error) => {
                startup_failed(&app, error);
                return;
            }
        };
        let data = match app.path().app_local_data_dir() {
            Ok(path) => path,
            Err(_) => {
                startup_failed(
                    &app,
                    "Nie można odnaleźć katalogu danych ScreenSort.".into(),
                );
                return;
            }
        };
        let update_app = app.clone();
        let result = processes::start(&runtime, &data, move |status, backend| {
            let desktop = update_app.state::<Desktop>();
            *desktop.status.lock().expect("status lock") = status;
            if let Some(value) = backend {
                *desktop.backend.lock().expect("backend lock") = Some(value);
            }
        })
        .await;
        match result {
            Ok(owner) => {
                *app.state::<Desktop>().owner.lock().await = Some(owner);
                loop {
                    tauri::async_runtime::spawn_blocking(|| {
                        std::thread::sleep(std::time::Duration::from_secs(2))
                    })
                    .await
                    .ok();
                    let desktop = app.state::<Desktop>();
                    if desktop.closing.load(Ordering::SeqCst) {
                        break;
                    }
                    let stopped = desktop
                        .owner
                        .lock()
                        .await
                        .as_mut()
                        .and_then(|owner| owner.stopped_child());
                    if let Some(name) = stopped {
                        let mut status = desktop.status.lock().expect("status lock");
                        status.analysis_ready = false;
                        if name != "Python" {
                            status.backend_ready = false;
                        }
                        status.error = Some(format!("Proces {name} zakończył pracę. Uruchom ScreenSort ponownie; zapisane dane pozostają w bazie."));
                        if name != "Python" {
                            break;
                        }
                    }
                }
            }
            Err(message) => startup_failed(&app, message),
        }
    });
}

pub async fn stop(app: &AppHandle) {
    let owner = app.state::<Desktop>().owner.lock().await.take();
    if let Some(mut owner) = owner {
        owner.stop().await;
    }
}

pub fn begin_close(app: &AppHandle) -> bool {
    app.state::<Desktop>().packages.cancel();
    !app.state::<Desktop>().closing.swap(true, Ordering::SeqCst)
}

#[tauri::command]
pub fn ai_package_status(app: AppHandle) -> Result<packages::PackageStatus, String> {
    let data = app
        .path()
        .app_local_data_dir()
        .map_err(|_| "Brak katalogu danych aplikacji.")?;
    app.state::<Desktop>()
        .packages
        .status(&data, &runtime_path(&app)?)
}

#[tauri::command]
pub fn cancel_ai_package(state: State<'_, Desktop>) {
    state.packages.cancel();
}

#[tauri::command]
pub fn install_ai_package(app: AppHandle, id: String, from_file: bool) -> Result<(), String> {
    use tauri_plugin_dialog::DialogExt;
    let data = app
        .path()
        .app_local_data_dir()
        .map_err(|_| "Brak katalogu danych aplikacji.")?;
    let spec = packages::catalog_for_command(&id)?;
    {
        let desktop = app.state::<Desktop>();
        // Same lock order as model startup: no package activation while Python starts/runs.
        let status = desktop.status.lock().map_err(|_| "Brak stanu aplikacji.")?;
        if !status.backend_ready
            || status.analysis_ready
            || status.analysis_starting
            || desktop.closing.load(Ordering::SeqCst)
        {
            return Err(
                "Zainstaluj pakiet po uruchomieniu katalogu, ale przed uruchomieniem AI.".into(),
            );
        }
        desktop.packages.begin()?;
    }
    tauri::async_runtime::spawn(async move {
        let worker_app = app.clone();
        let result = tauri::async_runtime::spawn_blocking(move || {
            let desktop = worker_app.state::<Desktop>();
            let local = if from_file {
                let selected = worker_app
                    .dialog()
                    .file()
                    .add_filter("Pakiet AI ScreenSort", &["zip"])
                    .blocking_pick_file()
                    .ok_or_else(packages::cancelled_message)?;
                Some(
                    selected
                        .into_path()
                        .map_err(|_| "Wybrany plik nie jest lokalnym ZIP.")?,
                )
            } else {
                None
            };
            desktop.packages.install(&spec, &data, local.as_deref())
        })
        .await
        .unwrap_or_else(|_| {
            Err("Instalacja została przerwana. Dotychczasowy pakiet pozostaje bez zmian.".into())
        });
        app.state::<Desktop>().packages.finish(result);
    });
    Ok(())
}

#[tauri::command]
pub fn desktop_status(state: State<'_, Desktop>) -> Status {
    state.status.lock().expect("status lock").clone()
}

#[tauri::command]
pub async fn start_analysis_runtime(app: AppHandle) -> Result<(), String> {
    {
        let desktop = app.state::<Desktop>();
        let mut status = desktop
            .status
            .lock()
            .map_err(|_| "Nie można odczytać stanu.")?;
        if !status.backend_ready {
            return Err("Poczekaj na uruchomienie katalogu.".into());
        }
        if desktop.packages.busy() {
            return Err("Poczekaj na zakończenie instalacji pakietu AI.".into());
        }
        if status.analysis_ready || status.analysis_starting {
            return Ok(());
        }
        status.analysis_starting = true;
        status.error = None;
        status.phase = "Przygotowujemy AI na Twoje żądanie…".into();
    }
    tauri::async_runtime::spawn(async move {
        let result = {
            let desktop = app.state::<Desktop>();
            let mut owner = desktop.owner.lock().await;
            match owner.as_mut() {
                Some(owner) => {
                    owner
                        .start_analysis(|| desktop.closing.load(Ordering::SeqCst))
                        .await
                }
                None => Err("Katalog nie jest gotowy. Spróbuj ponownie.".into()),
            }
        };
        let desktop = app.state::<Desktop>();
        let mut status = desktop.status.lock().expect("status lock");
        status.analysis_starting = false;
        status.analysis_ready = result.is_ok();
        status.phase = if result.is_ok() {
            "AI gotowe do analizy"
        } else {
            "AI niedostępne — katalog działa"
        }
        .into();
        status.error = result.err();
    });
    Ok(())
}

#[derive(Serialize)]
pub struct BackendReply {
    status: u16,
    body: Value,
}

fn allowed(method: &str, path: &str, image: bool) -> bool {
    if path.len() > 8192 || path.contains('#') || path.contains('\\') {
        return false;
    }
    let route = path.split('?').next().unwrap_or("");
    if image {
        return method == "GET"
            && Regex::new(r"^/api/catalog/files/[1-9][0-9]*/(thumbnail|preview)$")
                .unwrap()
                .is_match(route);
    }
    match method {
        "POST" => {
            matches!(
                route,
                "/api/scan/jobs" | "/api/analysis/jobs" | "/api/organization/batches"
            ) || Regex::new(r"^/api/organization/batches/[0-9a-fA-F-]{36}/control$")
                .unwrap()
                .is_match(route)
        }
        "PUT" => Regex::new(r"^/api/catalog/files/[1-9][0-9]*/naming$")
            .unwrap()
            .is_match(route),
        "GET" => {
            route == "/api/catalog/page"
                || route == "/api/organization/batches/latest"
                || Regex::new(r"^/api/organization/batches/[0-9a-fA-F-]{36}(/items)?$")
                    .unwrap()
                    .is_match(route)
                || Regex::new(r"^/api/(scan|analysis)/jobs/[0-9a-fA-F-]{36}(/rejections)?$")
                    .unwrap()
                    .is_match(route)
                || Regex::new(r"^/api/catalog/files/[1-9][0-9]*/(analysis|naming)$")
                    .unwrap()
                    .is_match(route)
        }
        _ => false,
    }
}

#[tauri::command]
pub async fn backend_request(
    method: String,
    path: String,
    body: Option<Value>,
    state: State<'_, Desktop>,
) -> Result<BackendReply, String> {
    if !allowed(&method, &path, false) {
        return Err("Niedozwolane żądanie aplikacji.".into());
    }
    if body
        .as_ref()
        .is_some_and(|value| value.to_string().len() > 8192)
    {
        return Err("Żądanie przekroczyło limit.".into());
    }
    let backend = state
        .backend
        .lock()
        .map_err(|_| "Backend jest niedostępny.")?
        .clone()
        .ok_or("Backend jest jeszcze uruchamiany.")?;
    let (status, bytes) = backend
        .request(&method, &path, body.as_ref(), 6 * 1024 * 1024)
        .await?;
    let text = String::from_utf8(bytes).map_err(|_| "Nieprawidłowa odpowiedź backendu.")?;
    let body = serde_json::from_str(&text).unwrap_or(Value::String(text));
    Ok(BackendReply { status, body })
}

#[tauri::command]
pub async fn backend_image(
    path: String,
    state: State<'_, Desktop>,
) -> Result<tauri::ipc::Response, String> {
    if !allowed("GET", &path, true) {
        return Err("Niedozwolony podgląd.".into());
    }
    let backend = state
        .backend
        .lock()
        .map_err(|_| "Backend jest niedostępny.")?
        .clone()
        .ok_or("Backend jest jeszcze uruchamiany.")?;
    let (status, bytes) = backend.request("GET", &path, None, 6 * 1024 * 1024).await?;
    if status != 200 {
        return Err("Podgląd jest niedostępny.".into());
    }
    Ok(tauri::ipc::Response::new(bytes))
}

#[cfg(test)]
mod tests {
    use super::allowed;
    #[test]
    fn bridge_exposes_only_the_product_api() {
        assert!(allowed("POST", "/api/analysis/jobs", false));
        assert!(allowed("POST", "/api/organization/batches", false));
        assert!(allowed(
            "GET",
            "/api/organization/batches/latest?folderPath=test",
            false
        ));
        assert!(allowed(
            "GET",
            "/api/organization/batches/00000000-0000-0000-0000-000000000001/items?page=1",
            false
        ));
        assert!(allowed(
            "POST",
            "/api/organization/batches/00000000-0000-0000-0000-000000000001/control",
            false
        ));
        assert!(!allowed("POST", "/_internal/analysis-connection", false));
        assert!(allowed("GET", "/api/catalog/page?folderPath=test", false));
        assert!(allowed("GET", "/api/catalog/files/1/thumbnail?v=x", true));
        assert!(allowed("GET", "/api/catalog/files/1/naming", false));
        assert!(allowed("PUT", "/api/catalog/files/1/naming", false));
        assert!(!allowed("POST", "/api/catalog/files/1/naming", false));
        assert!(!allowed("PUT", "/api/catalog/files/1/analysis", false));
        assert!(!allowed("PUT", "/api/catalog/files/0/naming", false));
        assert!(!allowed("PUT", "/api/catalog/files/../naming", false));
        for path in [
            "http://evil.test/",
            "//evil.test/",
            "/_internal/shutdown",
            "/api/catalog/files/../secret",
            "/api/catalog/files/0/preview",
        ] {
            assert!(!allowed("GET", path, true));
            assert!(!allowed("POST", path, false));
        }
    }
}
