mod desktop;
mod updates;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    if std::env::args().any(|arg| arg == "--smoke-test") {
        let code = tauri::async_runtime::block_on(desktop::run_smoke());
        std::process::exit(code);
    }
    let startup_check = std::env::args().any(|arg| arg == "--startup-check");
    let mut context = tauri::generate_context!();
    if startup_check {
        // The real Tauri app/path resolver, but no WebView profile in the user's data.
        context.config_mut().app.windows.clear();
    }
    let mut builder = tauri::Builder::default()
        .manage(desktop::Desktop::default())
        .manage(updates::Updates::default())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build());
    if !startup_check {
        builder = builder.plugin(tauri_plugin_single_instance::init(|app, _, _| {
            use tauri::Manager;
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.set_focus();
            }
        }));
    }
    builder
        .invoke_handler(tauri::generate_handler![
            desktop::desktop_status,
            desktop::ai_package_status,
            desktop::install_ai_package,
            desktop::cancel_ai_package,
            desktop::start_analysis_runtime,
            desktop::backend_request,
            desktop::backend_image,
            updates::app_update_status,
            updates::check_app_update,
            updates::download_app_update,
            updates::install_app_update,
            updates::restart_after_update_error
        ])
        .setup(move |app| {
            if startup_check {
                desktop::launch_startup_check(app.handle().clone());
            } else {
                updates::on_launch(app.handle());
                desktop::launch(app.handle().clone());
                desktop::spawn_events(app.handle().clone());
            }
            Ok(())
        })
        .build(context)
        .expect("error while building ScreenSort")
        .run(move |app, event| {
            if let tauri::RunEvent::ExitRequested {
                api, code: None, ..
            } = event
            {
                api.prevent_exit();
                if startup_check {
                    // No window is created; the check owns cleanup and its final exit code.
                    return;
                }
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
