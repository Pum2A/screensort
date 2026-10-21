//! Only the native process owns updater URLs, verified bytes and installation.
use futures_util::future::{AbortHandle, Abortable};
use serde::Serialize;
use std::{sync::Mutex, time::Duration};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_updater::{Update, UpdaterExt};

const MAX_UPDATE_BYTES: u64 = 512 * 1024 * 1024;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    phase: &'static str,
    current_version: String,
    version: Option<String>,
    notes: Option<String>,
    downloaded_bytes: u64,
    total_bytes: Option<u64>,
    error: Option<String>,
}

struct Pending {
    update: Update,
    bytes: Option<Vec<u8>>,
}
struct Inner {
    status: Status,
    pending: Option<Pending>,
    launch_checked: bool,
}
pub struct Updates(Mutex<Inner>);

impl Default for Updates {
    fn default() -> Self {
        Self(Mutex::new(Inner {
            pending: None,
            launch_checked: false,
            status: Status {
                phase: if cfg!(debug_assertions) {
                    "DEVELOPMENT"
                } else {
                    "IDLE"
                },
                current_version: env!("CARGO_PKG_VERSION").into(),
                version: None,
                notes: None,
                downloaded_bytes: 0,
                total_bytes: None,
                error: None,
            },
        }))
    }
}

impl Updates {
    fn claim_launch_check(&self) -> bool {
        let mut inner = self.0.lock().expect("update state");
        if inner.launch_checked {
            return false;
        }
        inner.launch_checked = true;
        true
    }
}

/// A fresh native process always checks; a webview reload never triggers another check.
/// No persisted 'already checked' flag, backend readiness dependency or startup blocking.
pub fn on_launch(app: &AppHandle) {
    if !app.state::<Updates>().claim_launch_check() {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let state = app.state::<Updates>();
        let _ = check(&app, &state).await;
    });
}

fn busy(phase: &str) -> bool {
    matches!(phase, "CHECKING" | "DOWNLOADING" | "INSTALLING")
}

fn trusted_url(url: &reqwest::Url) -> bool {
    url.scheme() == "https"
        && url.host_str() == Some("github.com")
        && url.port_or_known_default() == Some(443)
        && url.username().is_empty()
        && url.password().is_none()
        && url
            .path()
            .starts_with("/Pum2A/screensort-releases/releases/download/")
        && url.path().ends_with("-setup.exe")
        && url.query().is_none()
        && url.fragment().is_none()
}

#[tauri::command]
pub fn app_update_status(state: State<'_, Updates>) -> Status {
    state.0.lock().expect("update state").status.clone()
}

#[tauri::command]
pub async fn check_app_update(app: AppHandle, state: State<'_, Updates>) -> Result<Status, String> {
    check(&app, &state).await
}

async fn check(app: &AppHandle, state: &Updates) -> Result<Status, String> {
    {
        let mut inner = state.0.lock().expect("update state");
        if cfg!(debug_assertions)
            || busy(inner.status.phase)
            || inner.status.phase == "DOWNLOADED"
            || inner.status.phase == "RESTART_REQUIRED"
        {
            return Ok(inner.status.clone());
        }
        inner.status.phase = "CHECKING";
        inner.status.error = None;
    }
    let result = async {
        let updater = app
            .updater_builder()
            .timeout(Duration::from_secs(15))
            .build()
            .map_err(|_| ())?;
        let update = updater.check().await.map_err(|_| ())?;
        if update
            .as_ref()
            .is_some_and(|value| !trusted_url(&value.download_url))
        {
            return Err(());
        }
        Ok(update)
    }
    .await;
    let mut inner = state.0.lock().expect("update state");
    match result {
        Ok(update) => {
            inner.status.phase = if update.is_some() {
                "AVAILABLE"
            } else {
                "UP_TO_DATE"
            };
            inner.status.version = update.as_ref().map(|value| value.version.clone());
            inner.status.notes = update.as_ref().and_then(|value| value.body.clone());
            inner.pending = update.map(|update| Pending {
                update,
                bytes: None,
            });
        }
        Err(()) => {
            inner.status.phase = "ERROR";
            inner.status.error = Some("Nie udało się sprawdzić aktualizacji. Sprawdź połączenie i spróbuj ponownie. Katalog nadal działa.".into());
        }
    }
    Ok(inner.status.clone())
}

#[tauri::command]
pub async fn download_app_update(state: State<'_, Updates>) -> Result<Status, String> {
    let mut update = {
        let mut inner = state.0.lock().expect("update state");
        if cfg!(debug_assertions) || busy(inner.status.phase) {
            return Err("Aktualizacja nie jest teraz dostępna.".into());
        }
        let update = inner
            .pending
            .as_ref()
            .ok_or("Najpierw sprawdź aktualizacje.")?
            .update
            .clone();
        inner.status.phase = "DOWNLOADING";
        inner.status.error = None;
        inner.status.downloaded_bytes = 0;
        inner.status.total_bytes = None;
        update
    };
    // Checking the manifest is short; an explicitly requested download may take longer.
    update.timeout = Some(Duration::from_secs(900));
    let (abort, registration) = AbortHandle::new_pair();
    let result = Abortable::new(
        update.download(
            |count, total| {
                let mut inner = state.0.lock().expect("update state");
                inner.status.downloaded_bytes =
                    inner.status.downloaded_bytes.saturating_add(count as u64);
                inner.status.total_bytes = total;
                if inner.status.downloaded_bytes > MAX_UPDATE_BYTES
                    || total.is_some_and(|bytes| bytes > MAX_UPDATE_BYTES)
                {
                    abort.abort();
                }
            },
            || {},
        ),
        registration,
    )
    .await;
    let mut inner = state.0.lock().expect("update state");
    match result {
        Ok(Ok(bytes)) if bytes.len() as u64 <= MAX_UPDATE_BYTES => {
            // The plugin verifies both the signature and its bound app version before this point.
            inner.pending.as_mut().expect("pending update").bytes = Some(bytes);
            inner.status.phase = "DOWNLOADED";
        }
        _ => {
            inner.status.phase = "ERROR";
            inner.status.error = Some("Pobieranie lub weryfikacja aktualizacji nie powiodły się. Nie zmieniono aplikacji ani danych. Spróbuj ponownie.".into());
        }
    }
    Ok(inner.status.clone())
}

#[tauri::command]
pub async fn install_app_update(app: AppHandle, state: State<'_, Updates>) -> Result<(), String> {
    {
        let mut inner = state.0.lock().expect("update state");
        if cfg!(debug_assertions) || inner.status.phase != "DOWNLOADED" {
            return Err("Najpierw pobierz i sprawdź aktualizację.".into());
        }
        inner.status.phase = "INSTALLING";
    }
    if let Err(error) = crate::desktop::prepare_update(&app).await {
        state.0.lock().expect("update state").status.phase = "DOWNLOADED";
        return Err(error);
    }
    let (update, bytes) = {
        let mut inner = state.0.lock().expect("update state");
        let pending = inner.pending.as_mut().ok_or("Brak aktualizacji.")?;
        let bytes = pending
            .bytes
            .take()
            .ok_or("Brak zweryfikowanego instalatora.")?;
        let update = pending.update.clone();
        inner.status.phase = "INSTALLING";
        (update, bytes)
    };
    // The updater exits the process directly on Windows. Stop our DB before calling it.
    crate::desktop::stop(&app).await;
    let result = tauri::async_runtime::spawn_blocking(move || update.install(bytes)).await;
    if !matches!(result, Ok(Ok(()))) {
        crate::desktop::update_failed(&app);
        let mut inner = state.0.lock().expect("update state");
        inner.status.phase = "RESTART_REQUIRED";
        inner.status.error = Some("Nie udało się uruchomić instalatora. Dane zachowano; uruchom ScreenSort ponownie, aby wrócić do katalogu.".into());
        return Err("Nie udało się uruchomić instalatora.".into());
    }
    Ok(())
}

#[tauri::command]
pub fn restart_after_update_error(app: AppHandle, state: State<'_, Updates>) -> Result<(), String> {
    if state.0.lock().expect("update state").status.phase != "RESTART_REQUIRED" {
        return Err("Restart nie jest wymagany.".into());
    }
    app.restart();
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn each_process_checks_at_launch_but_webview_remounts_do_not() {
        let first_process = Updates::default();
        assert!(first_process.claim_launch_check());
        assert!(!first_process.claim_launch_check());
        let next_process = Updates::default();
        assert!(next_process.claim_launch_check());
    }
    #[test]
    fn update_downloads_are_only_from_our_https_distribution() {
        for value in ["https://github.com/Pum2A/screensort-releases/releases/download/v0.2.3/ScreenSort_0.2.3_x64-setup.exe"] {
            assert!(trusted_url(&value.parse().unwrap()));
        }
        for value in ["http://github.com/Pum2A/screensort-releases/releases/download/v1/a-setup.exe", "https://github.com/evil/screensort-releases/releases/download/v1/a-setup.exe", "https://github.com.evil.test/Pum2A/screensort-releases/releases/download/v1/a-setup.exe", "https://github.com/Pum2A/screensort-releases/releases/download/v1/a.zip", "https://github.com/Pum2A/screensort-releases/releases/download/v1/a-setup.exe?url=evil"] {
            assert!(!trusted_url(&value.parse().unwrap()));
        }
    }
    #[test]
    fn downloads_cannot_overlap_check_or_install() {
        assert!(busy("CHECKING"));
        assert!(busy("DOWNLOADING"));
        assert!(busy("INSTALLING"));
        assert!(!busy("AVAILABLE"));
        assert!(!busy("DOWNLOADED"));
    }
}
