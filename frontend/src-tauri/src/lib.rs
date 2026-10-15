mod desktop;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    if std::env::args().any(|arg| arg == "--smoke-test") {
        let code = tauri::async_runtime::block_on(desktop::run_smoke());
        std::process::exit(code);
    }
    tauri::Builder::default()
        .manage(desktop::Desktop::default())
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            use tauri::Manager;
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            desktop::desktop_status,
            desktop::backend_request,
            desktop::backend_image
        ])
        .setup(|app| {
            desktop::launch(app.handle().clone());
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building ScreenSort")
        .run(|app, event| {
            if let tauri::RunEvent::ExitRequested {
                api, code: None, ..
            } = event
            {
                api.prevent_exit();
                if desktop::begin_close(app) {
                    let app = app.clone();
                    tauri::async_runtime::spawn(async move {
                        desktop::stop(&app).await;
                        app.exit(0);
                    });
                }
            }
        });
}
