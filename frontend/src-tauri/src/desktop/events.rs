//! Pushes status changes to the window as Tauri events, so the WebView does not poll every second.
//! Comparing inside the native process is cheap; only a real change crosses into the WebView.

use super::{ai_package_status, processes::pause, Desktop};
use serde::Serialize;
use std::{sync::atomic::Ordering, time::Duration};
use tauri::{AppHandle, Emitter, Manager};

pub const DESKTOP_STATUS: &str = "desktop-status";
pub const AI_PACKAGE_STATUS: &str = "ai-package-status";
const STATUS_EVERY: Duration = Duration::from_millis(250);
// Package status reads the package catalog and receipts from disk, so it is checked less often.
const PACKAGES_EVERY_TICKS: u32 = 4;

/// Remembers the last value sent and says whether a new one differs.
#[derive(Default)]
struct Changes(Option<String>);

impl Changes {
    fn changed<T: Serialize>(&mut self, value: &T) -> bool {
        let Ok(text) = serde_json::to_string(value) else {
            return false;
        };
        if self.0.as_deref() == Some(text.as_str()) {
            return false;
        }
        self.0 = Some(text);
        true
    }
}

pub fn spawn(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let mut status_sent = Changes::default();
        let mut packages_sent = Changes::default();
        for tick in 0_u32.. {
            let desktop = app.state::<Desktop>();
            if desktop.closing.load(Ordering::SeqCst) {
                break;
            }
            if let Ok(status) = desktop.status.lock().map(|status| status.clone()) {
                if status_sent.changed(&status) {
                    let _ = app.emit(DESKTOP_STATUS, &status);
                }
            }
            if tick % PACKAGES_EVERY_TICKS == 0 {
                if let Ok(packages) = ai_package_status(app.clone()) {
                    if packages_sent.changed(&packages) {
                        let _ = app.emit(AI_PACKAGE_STATUS, &packages);
                    }
                }
            }
            pause(STATUS_EVERY).await;
        }
    });
}

#[cfg(test)]
mod tests {
    use super::Changes;

    #[test]
    fn only_a_different_value_counts_as_a_change() {
        let mut changes = Changes::default();
        assert!(changes.changed(&"starting"));
        assert!(!changes.changed(&"starting"));
        assert!(changes.changed(&"ready"));
        assert!(!changes.changed(&"ready"));
    }
}
