//! Trusted native bridge: ports and session credentials never enter the webview.

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

pub use smoke::run_smoke;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub phase: String,
    pub backend_ready: bool,
    pub analysis_ready: bool,
    pub error: Option<String>,
}

impl Default for Status {
    fn default() -> Self {
        Self {
            phase: "Przygotowujemy aplikację…".into(),
            backend_ready: false,
            analysis_ready: false,
            error: None,
        }
    }
}

pub struct Desktop {
    status: Mutex<Status>,
    backend: Mutex<Option<processes::Backend>>,
    owner: Mutex<Option<processes::Owner>>,
    closing: AtomicBool,
}

impl Default for Desktop {
    fn default() -> Self {
        Self {
            status: Mutex::new(Status::default()),
            backend: Mutex::new(None),
            owner: Mutex::new(None),
            closing: AtomicBool::new(false),
        }
    }
}

pub fn launch(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let runtime = if cfg!(debug_assertions) {
            std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime")
        } else {
            match app.path().resource_dir() {
                Ok(path) => path.join("runtime"),
                Err(_) => return,
            }
        };
        let data = match app.path().app_local_data_dir() {
            Ok(path) => path,
            Err(_) => return,
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
                *app.state::<Desktop>().owner.lock().expect("owner lock") = Some(owner);
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
                        .expect("owner lock")
                        .as_mut()
                        .and_then(|owner| owner.stopped_child());
                    if let Some(name) = stopped {
                        let mut status = desktop.status.lock().expect("status lock");
                        status.analysis_ready = false;
                        if name != "Python" {
                            status.backend_ready = false;
                        }
                        status.error = Some(format!("Proces {name} zakończył pracę. Uruchom ScreenSort ponownie; zapisane dane pozostają w bazie."));
                        break;
                    }
                }
            }
            Err(message) => {
                let desktop = app.state::<Desktop>();
                let mut status = desktop.status.lock().expect("status lock");
                status.backend_ready = false;
                status.analysis_ready = false;
                status.error = Some(message);
            }
        }
    });
}

pub async fn stop(app: &AppHandle) {
    let owner = app
        .state::<Desktop>()
        .owner
        .lock()
        .expect("owner lock")
        .take();
    if let Some(mut owner) = owner {
        owner.stop().await;
    }
}

pub fn begin_close(app: &AppHandle) -> bool {
    !app.state::<Desktop>().closing.swap(true, Ordering::SeqCst)
}

#[tauri::command]
pub fn desktop_status(state: State<'_, Desktop>) -> Status {
    state.status.lock().expect("status lock").clone()
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
        "POST" => matches!(route, "/api/scan/jobs" | "/api/analysis/jobs"),
        "GET" => {
            route == "/api/catalog/page"
                || Regex::new(r"^/api/(scan|analysis)/jobs/[0-9a-fA-F-]{36}(/rejections)?$")
                    .unwrap()
                    .is_match(route)
                || Regex::new(r"^/api/catalog/files/[1-9][0-9]*/analysis$")
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
        assert!(allowed("GET", "/api/catalog/page?folderPath=test", false));
        assert!(allowed("GET", "/api/catalog/files/1/thumbnail?v=x", true));
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
