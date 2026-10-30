//! Trusted native bridge: ports and session credentials never enter the webview.

mod events;
mod packages;
mod paths;
mod processes;
mod smoke;
mod timings;

use regex::Regex;
use serde::Serialize;
use serde_json::Value;
use std::sync::{
    atomic::{AtomicBool, AtomicUsize, Ordering},
    LazyLock, Mutex, MutexGuard, PoisonError,
};
use tauri::{AppHandle, Manager, State};

pub use events::spawn as spawn_events;
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
    /// True while processes::start runs; closing then waits for it to stop what it started.
    starting: AtomicBool,
    requests: AtomicUsize,
    updating: AtomicBool,
    packages: packages::PackageManager,
}

impl Default for Desktop {
    fn default() -> Self {
        Self {
            status: Mutex::new(Status::default()),
            backend: Mutex::new(None),
            owner: tauri::async_runtime::Mutex::new(None),
            closing: AtomicBool::new(false),
            starting: AtomicBool::new(true),
            requests: AtomicUsize::new(0),
            updating: AtomicBool::new(false),
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
    let mut status = lock(&desktop.status);
    status.backend_ready = false;
    status.analysis_ready = false;
    status.error = Some(error);
}

pub fn launch(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let result = start_processes(&app).await;
        let desktop = app.state::<Desktop>();
        let result = match result {
            // The window closed while we were starting: stop cleanly instead of keeping the owner.
            Ok(mut owner) if desktop.closing.load(Ordering::SeqCst) => {
                owner.stop().await;
                Err("Aplikacja kończy pracę.".to_string())
            }
            other => other,
        };
        match result {
            Ok(owner) => {
                *desktop.owner.lock().await = Some(owner);
                desktop.starting.store(false, Ordering::SeqCst);
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
                    let stopped = {
                        let mut guard = desktop.owner.lock().await;
                        match guard.as_mut() {
                            Some(owner) => {
                                let stopped = owner.stopped_child();
                                // An AI engine is optional: forget the dead child so the catalog
                                // keeps running and the next "start" begins from a clean slate.
                                if let Some(name @ ("Python" | "Llama")) = stopped {
                                    owner.retire_engine(name);
                                }
                                stopped
                            }
                            None => None,
                        }
                    };
                    if stopped == Some("Java") && restart_backend(&app).await {
                        continue;
                    }
                    if let Some(name) = stopped {
                        let engine = matches!(name, "Python" | "Llama");
                        let mut status = lock(&desktop.status);
                        status.analysis_ready = false;
                        if !engine {
                            status.backend_ready = false;
                        }
                        status.error = Some(if engine {
                            "Silnik analizy zakończył pracę. Uruchom go ponownie w panelu „Silnik analizy”; katalog działa dalej.".into()
                        } else {
                            format!("Proces {name} zakończył pracę. Uruchom ScreenSort ponownie; zapisane dane pozostają w bazie.")
                        });
                        if !engine {
                            break;
                        }
                    }
                }
            }
            Err(message) => {
                desktop.starting.store(false, Ordering::SeqCst);
                startup_failed(&app, message);
            }
        }
    });
}

async fn start_processes(app: &AppHandle) -> Result<processes::Owner, String> {
    let runtime = runtime_path(app)?;
    let data = app
        .path()
        .app_local_data_dir()
        .map_err(|_| "Nie można odnaleźć katalogu danych ScreenSort.".to_string())?;
    let update_app = app.clone();
    let closing_app = app.clone();
    processes::start(
        &runtime,
        &data,
        move |status, backend| {
            let desktop = update_app.state::<Desktop>();
            *lock(&desktop.status) = status;
            if let Some(value) = backend {
                *lock(&desktop.backend) = Some(value);
            }
        },
        move || {
            closing_app
                .state::<Desktop>()
                .closing
                .load(Ordering::SeqCst)
        },
    )
    .await
}

/// Java stopped while the app runs: start it again on the same database. False when it gave up.
async fn restart_backend(app: &AppHandle) -> bool {
    let desktop = app.state::<Desktop>();
    {
        let mut status = lock(&desktop.status);
        status.backend_ready = false;
        status.error = None;
        status.phase = "Backend przestał odpowiadać. Uruchamiamy go ponownie…".into();
    }
    *lock(&desktop.backend) = None;
    let closing_app = app.clone();
    let mut cancelled = move || {
        closing_app
            .state::<Desktop>()
            .closing
            .load(Ordering::SeqCst)
    };
    let result = match desktop.owner.lock().await.as_mut() {
        Some(owner) => owner.restart_java(&mut cancelled).await,
        None => return false,
    };
    let mut status = lock(&desktop.status);
    match result {
        Ok((backend, analysis)) => {
            *lock(&desktop.backend) = Some(backend);
            status.backend_ready = true;
            status.analysis_ready = analysis;
            status.phase = "Katalog gotowy. Backend uruchomiono ponownie.".into();
            true
        }
        Err(message) => {
            status.analysis_ready = false;
            status.error = Some(message);
            false
        }
    }
}

pub async fn stop(app: &AppHandle) {
    let desktop = app.state::<Desktop>();
    // A start in progress sees `closing` at its next step and stops what it started itself.
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
    while desktop.starting.load(Ordering::SeqCst) && std::time::Instant::now() < deadline {
        processes::pause(std::time::Duration::from_millis(100)).await;
    }
    let owner = desktop.owner.lock().await.take();
    if let Some(mut owner) = owner {
        owner.stop().await;
    }
}

pub fn begin_close(app: &AppHandle) -> bool {
    app.state::<Desktop>().packages.cancel();
    !app.state::<Desktop>().closing.swap(true, Ordering::SeqCst)
}

pub async fn prepare_update(app: &AppHandle) -> Result<(), String> {
    let desktop = app.state::<Desktop>();
    {
        let status = desktop.status.lock().map_err(|_| "Brak stanu aplikacji.")?;
        if !status.backend_ready || status.analysis_starting || desktop.packages.busy() {
            return Err("Poczekaj na zakończenie uruchamiania lub instalacji silnika.".into());
        }
        if desktop.closing.load(Ordering::SeqCst) || desktop.updating.swap(true, Ordering::SeqCst) {
            return Err("Aplikacja już kończy pracę.".into());
        }
    }
    let ready = async {
        if desktop.requests.load(Ordering::SeqCst) != 0 {
            return Err(());
        }
        let backend = desktop.backend.lock().map_err(|_| ())?.clone().ok_or(())?;
        let (code, bytes) = backend
            .request("GET", "/_internal/update-ready", None, 4096)
            .await
            .map_err(|_| ())?;
        let value: Value = serde_json::from_slice(&bytes).map_err(|_| ())?;
        if code != 200 || value.get("ready").and_then(Value::as_bool) != Some(true) {
            return Err(());
        }
        Ok(())
    }
    .await;
    if ready.is_err() {
        desktop.updating.store(false, Ordering::SeqCst);
        return Err(
            "Zakończ skan, wstrzymaj analizę i poczekaj na bieżące operacje przed instalacją."
                .into(),
        );
    }
    desktop.closing.store(true, Ordering::SeqCst);
    Ok(())
}

pub fn update_failed(app: &AppHandle) {
    app.state::<Desktop>()
        .updating
        .store(false, Ordering::SeqCst);
    app.state::<Desktop>()
        .closing
        .store(false, Ordering::SeqCst);
    startup_failed(
        app,
        "Instalator nie został uruchomiony. Uruchom ScreenSort ponownie; dane pozostały bez zmian."
            .into(),
    );
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
            || desktop.updating.load(Ordering::SeqCst)
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
    lock(&state.status).clone()
}

/// Automatic tries per click. After the last one the user can press "start" again; no app restart.
const ANALYSIS_ATTEMPTS: u32 = 3;

/// Growing pause before the given attempt: 2 s before the second, 6 s before the third.
fn analysis_retry_delay(attempt: u32) -> std::time::Duration {
    std::time::Duration::from_secs(match attempt {
        0 | 1 => 0,
        2 => 2,
        _ => 6,
    })
}

#[cfg(test)]
mod analysis_retry_tests {
    use super::{analysis_retry_delay, ANALYSIS_ATTEMPTS};
    use std::time::Duration;

    #[test]
    fn analysis_start_retries_with_growing_pauses() {
        let pauses: Vec<Duration> = (1..=ANALYSIS_ATTEMPTS).map(analysis_retry_delay).collect();
        assert_eq!(
            pauses,
            [
                Duration::ZERO,
                Duration::from_secs(2),
                Duration::from_secs(6)
            ]
        );
    }
}

#[tauri::command]
pub async fn start_analysis_runtime(app: AppHandle) -> Result<(), String> {
    {
        let desktop = app.state::<Desktop>();
        let mut status = desktop
            .status
            .lock()
            .map_err(|_| "Nie można odczytać stanu.")?;
        if !status.backend_ready
            || desktop.updating.load(Ordering::SeqCst)
            || desktop.closing.load(Ordering::SeqCst)
        {
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
        let mut result = Err("Katalog nie jest gotowy. Spróbuj ponownie.".to_string());
        for attempt in 1..=ANALYSIS_ATTEMPTS {
            let desktop = app.state::<Desktop>();
            if attempt > 1 {
                if let Ok(mut status) = desktop.status.lock() {
                    status.phase = format!(
                        "AI nie wystartowało. Ponawiamy (próba {attempt} z {ANALYSIS_ATTEMPTS})…"
                    );
                }
                processes::pause(analysis_retry_delay(attempt)).await;
            }
            if desktop.closing.load(Ordering::SeqCst) {
                break;
            }
            let mut owner = desktop.owner.lock().await;
            let Some(owner) = owner.as_mut() else { break };
            // A missing AI package fails the same way every time: say so at once.
            if let Err(message) = owner.analysis_package() {
                result = Err(message);
                break;
            }
            result = owner
                .start_analysis(|| desktop.closing.load(Ordering::SeqCst))
                .await;
            if result.is_ok() {
                break;
            }
        }
        let desktop = app.state::<Desktop>();
        let mut status = lock(&desktop.status);
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

/// Route patterns for the bridge, compiled once on first use instead of on every request.
/// A pattern that fails to compile is `None` and matches nothing, so the bridge fails closed.
struct Routes {
    image: Option<Regex>,
    batch_command: Option<Regex>,
    naming: Option<Regex>,
    review: Option<Regex>,
    batch: Option<Regex>,
    export: Option<Regex>,
    job: Option<Regex>,
    file_details: Option<Regex>,
}

static ROUTES: LazyLock<Routes> = LazyLock::new(|| Routes {
    image: Regex::new(r"^/api/catalog/files/[1-9][0-9]*/(thumbnail|preview)$").ok(),
    batch_command: Regex::new(r"^/api/organization/batches/[0-9a-fA-F-]{36}/(control|exports)$")
        .ok(),
    naming: Regex::new(r"^/api/catalog/files/[1-9][0-9]*/naming$").ok(),
    review: Regex::new(r"^/api/organization/batches/[0-9a-fA-F-]{36}/items/[1-9][0-9]*/review$")
        .ok(),
    batch: Regex::new(
        r"^/api/organization/batches/[0-9a-fA-F-]{36}(/items|/plan|/exports/latest)?$",
    )
    .ok(),
    export: Regex::new(r"^/api/organization/exports/[0-9a-fA-F-]{36}(/issues)?$").ok(),
    job: Regex::new(r"^/api/(scan|analysis)/jobs/[0-9a-fA-F-]{36}(/rejections)?$").ok(),
    file_details: Regex::new(r"^/api/catalog/files/[1-9][0-9]*/(analysis|naming)$").ok(),
});

fn matches(pattern: &Option<Regex>, route: &str) -> bool {
    pattern.as_ref().is_some_and(|regex| regex.is_match(route))
}

fn allowed(method: &str, path: &str, image: bool) -> bool {
    if path.len() > 8192 || path.contains('#') || path.contains('\\') {
        return false;
    }
    let route = path.split('?').next().unwrap_or("");
    let routes = &*ROUTES;
    if image {
        return method == "GET" && matches(&routes.image, route);
    }
    match method {
        "POST" => {
            matches!(
                route,
                "/api/scan/jobs" | "/api/analysis/jobs" | "/api/organization/batches"
            ) || matches(&routes.batch_command, route)
        }
        "PUT" => {
            route == "/api/settings/naming-language"
                || matches(&routes.naming, route)
                || matches(&routes.review, route)
        }
        "GET" => {
            matches!(
                route,
                "/api/catalog/page"
                    | "/api/library"
                    | "/api/organization/batches/latest"
                    | "/api/settings/naming-language"
            ) || matches(&routes.review, route)
                || matches(&routes.batch, route)
                || matches(&routes.export, route)
                || matches(&routes.job, route)
                || matches(&routes.file_details, route)
        }
        _ => false,
    }
}

/// A panic while holding a lock "poisons" it. The guarded values here are plain status data that
/// stay consistent, so the app keeps using them instead of crashing on the next lock.
fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

#[tauri::command]
pub async fn backend_request(
    method: String,
    path: String,
    body: Option<Value>,
    state: State<'_, Desktop>,
) -> Result<BackendReply, String> {
    state.requests.fetch_add(1, Ordering::SeqCst);
    struct RequestGuard<'a>(&'a AtomicUsize);
    impl Drop for RequestGuard<'_> {
        fn drop(&mut self) {
            self.0.fetch_sub(1, Ordering::SeqCst);
        }
    }
    let _request = RequestGuard(&state.requests);
    if state.updating.load(Ordering::SeqCst) || state.closing.load(Ordering::SeqCst) {
        return Err(
            "Aplikacja przygotowuje aktualizację. Poczekaj na ponowne uruchomienie.".into(),
        );
    }
    if !allowed(&method, &path, false) {
        return Err("Niedozwolone żądanie aplikacji.".into());
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
        return Err(image_failure(status).into());
    }
    Ok(tauri::ipc::Response::new(bytes))
}

#[cfg(test)]
mod image_failure_tests {
    use super::image_failure;

    #[test]
    fn image_failures_keep_the_reason_and_hide_the_rest() {
        assert_eq!(image_failure(404), "image:missing");
        assert_eq!(image_failure(403), "image:blocked");
        assert_eq!(image_failure(409), "image:changed");
        assert_eq!(image_failure(413), "image:too-large");
        assert_eq!(image_failure(422), "image:unsupported");
        assert_eq!(image_failure(503), "image:busy");
        assert_eq!(image_failure(500), "image:unavailable");
    }
}

/// Stable reason codes for a failed preview. The WebView turns each into its own text and decides
/// whether trying again can help. Statuses mirror `CatalogImageController`.
fn image_failure(status: u16) -> &'static str {
    match status {
        404 => "image:missing",
        403 => "image:blocked",
        409 => "image:changed",
        413 => "image:too-large",
        422 => "image:unsupported",
        503 => "image:busy",
        _ => "image:unavailable",
    }
}

#[cfg(test)]
mod tests {
    use super::{allowed, lock, ROUTES};
    use std::sync::{Arc, Mutex};

    #[test]
    fn every_route_pattern_compiles() {
        let routes = &*ROUTES;
        for pattern in [
            &routes.image,
            &routes.batch_command,
            &routes.naming,
            &routes.review,
            &routes.batch,
            &routes.export,
            &routes.job,
            &routes.file_details,
        ] {
            assert!(pattern.is_some());
        }
    }

    #[test]
    fn a_poisoned_status_lock_still_answers() {
        let status = Arc::new(Mutex::new(7));
        let shared = Arc::clone(&status);
        let _ = std::thread::spawn(move || {
            let _guard = shared.lock().unwrap();
            panic!("poison the lock");
        })
        .join();
        assert!(status.is_poisoned());
        assert_eq!(*lock(&status), 7);
    }
    #[test]
    fn bridge_supports_copying_and_only_its_public_routes() {
        let batch = "/api/organization/batches/00000000-0000-0000-0000-000000000001";
        let export = "/api/organization/exports/00000000-0000-0000-0000-000000000002";
        assert!(allowed("GET", &format!("{batch}/plan?page=1"), false));
        assert!(allowed("POST", &format!("{batch}/exports"), false));
        assert!(allowed("GET", &format!("{batch}/exports/latest"), false));
        assert!(allowed("GET", export, false));
        assert!(allowed("GET", &format!("{export}/issues?page=1"), false));
        for method in ["POST", "PUT", "DELETE"] {
            assert!(!allowed(method, export, false));
            assert!(!allowed(method, &format!("{batch}/plan"), false));
        }
        assert!(!allowed("GET", &format!("{batch}/exports"), false));
        assert!(!allowed("GET", &format!("{export}/../secret"), false));
        assert!(!allowed("GET", &format!("{export}/issues"), true));
    }

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
        assert!(!allowed("POST", "/_internal/vision-connection", false));
        assert!(!allowed("GET", "/_internal/update-ready", false));
        assert!(allowed("GET", "/api/catalog/page?folderPath=test", false));
        assert!(allowed("GET", "/api/library?folderPath=test", false));
        assert!(!allowed("POST", "/api/library", false));
        assert!(allowed("GET", "/api/settings/naming-language", false));
        assert!(allowed("PUT", "/api/settings/naming-language", false));
        assert!(!allowed("POST", "/api/settings/naming-language", false));
        assert!(!allowed("PUT", "/api/settings/other", false));
        assert!(!allowed("GET", "/api/library/../catalog", false));
        assert!(allowed("GET", "/api/catalog/files/1/thumbnail?v=x", true));
        assert!(allowed("GET", "/api/catalog/files/1/naming", false));
        assert!(allowed("PUT", "/api/catalog/files/1/naming", false));
        assert!(!allowed("POST", "/api/catalog/files/1/naming", false));
        assert!(!allowed("PUT", "/api/catalog/files/1/analysis", false));
        assert!(!allowed("PUT", "/api/catalog/files/0/naming", false));
        assert!(!allowed("PUT", "/api/catalog/files/../naming", false));
        for method in ["GET", "PUT"] {
            assert!(allowed(
                method,
                "/api/organization/batches/00000000-0000-0000-0000-000000000001/items/1/review",
                false
            ));
            for id in ["0", "-1", "../1", "1/export", "1/rename"] {
                assert!(!allowed(method, &format!("/api/organization/batches/00000000-0000-0000-0000-000000000001/items/{id}/review"), false));
            }
        }
        assert!(!allowed(
            "POST",
            "/api/organization/batches/00000000-0000-0000-0000-000000000001/items/1/review",
            false
        ));
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
