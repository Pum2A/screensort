//! Explicit, isolated packaging check. It never scans the user's photographs.
use super::processes::{self, Backend};
use serde_json::{json, Value};
use std::{
    fs,
    path::{Path, PathBuf},
    time::{Duration, Instant},
};

const PNG: &str = "89504e470d0a1a0a0000000d4948445200000020000000200802000000fc18eda30000003749444154789cedd1c10d003008c3c094fd774e47301f7ebe0182645edb5c9ad3f57860c11f20132113211321132113211321132113857cc7f1033d38c33a7c0000000049454e44ae426082";
const HASH: &str = "74b0c61828abf9c34abcaef98e33d0512e72427975827ef36c41c73e4df479e5";

fn argument(name: &str) -> Result<PathBuf, String> {
    let mut args = std::env::args();
    while let Some(arg) = args.next() {
        if arg == name {
            return args
                .next()
                .map(PathBuf::from)
                .ok_or_else(|| format!("Missing {name}"));
        }
    }
    Err(format!("Missing {name}"))
}

async fn request(
    backend: &Backend,
    method: &str,
    path: &str,
    body: Option<&Value>,
) -> Result<Value, String> {
    let (status, bytes) = backend.request(method, path, body, 3 * 1024 * 1024).await?;
    if !(200..300).contains(&status) {
        return Err(format!(
            "Smoke request {method} {} returned {status}: {}",
            path.split('?').next().unwrap_or(path),
            String::from_utf8_lossy(&bytes)
        ));
    }
    serde_json::from_slice(&bytes).map_err(|_| "Invalid smoke response JSON".into())
}

async fn completed(backend: &Backend, path: &str) -> Result<Value, String> {
    let deadline = Instant::now() + Duration::from_secs(180);
    while Instant::now() < deadline {
        let job = request(backend, "GET", path, None).await?;
        match job["status"].as_str() {
            Some("COMPLETED") => return Ok(job),
            Some("FAILED") => return Err(format!("Smoke job failed: {}", job["errorMessage"])),
            _ => processes::pause(Duration::from_millis(300)).await,
        }
    }
    Err("Smoke job timed out".into())
}

async fn exercise(backend: &Backend, source: &Path, include_analysis: bool) -> Result<u64, String> {
    let job = request(
        backend,
        "POST",
        "/api/scan/jobs",
        Some(&json!({"folderPath": source})),
    )
    .await?;
    completed(
        backend,
        &format!(
            "/api/scan/jobs/{}",
            job["id"].as_str().ok_or("Missing scan ID")?
        ),
    )
    .await?;
    let mut url = reqwest::Url::parse("http://127.0.0.1/api/catalog/page").unwrap();
    url.query_pairs_mut()
        .append_pair("folderPath", &source.to_string_lossy())
        .append_pair("pageNumber", "0")
        .append_pair("pageSize", "1");
    let page = request(
        backend,
        "GET",
        &format!("{}?{}", url.path(), url.query().unwrap()),
        None,
    )
    .await?;
    let id = page["files"][0]["id"]
        .as_u64()
        .ok_or("Synthetic image missing from catalog")?;
    if include_analysis {
        let job = request(
            backend,
            "POST",
            "/api/analysis/jobs",
            Some(&json!({"fileId": id, "ocr": true, "visual": true})),
        )
        .await?;
        let done = completed(
            backend,
            &format!(
                "/api/analysis/jobs/{}",
                job["id"].as_str().ok_or("Missing analysis ID")?
            ),
        )
        .await?;
        let analysis = &done["analysis"]["result"];
        if analysis["source_sha256"] != HASH
            || analysis["ocr"]["status"] != "COMPLETED"
            || analysis["visual"]["status"] != "COMPLETED"
        {
            return Err(format!("Unexpected analysis result: {done}"));
        }
        let batch = request(
            backend,
            "POST",
            "/api/organization/batches",
            Some(&json!({"folderPath": source, "ocr": true, "visual": true})),
        )
        .await?;
        let result = completed(
            backend,
            &format!(
                "/api/organization/batches/{}",
                batch["id"].as_str().ok_or("Missing batch ID")?
            ),
        )
        .await?;
        if result["total"] != 1 || result["completed"] != 1 || result["failed"] != 0 {
            return Err("Batch did not process the synthetic catalog entry".into());
        }
    }
    let (_, image) = backend
        .request(
            "GET",
            &format!("/api/catalog/files/{id}/thumbnail"),
            None,
            6 * 1024 * 1024,
        )
        .await?;
    if !image.starts_with(b"\x89PNG") {
        return Err("Thumbnail did not contain a PNG".into());
    }
    Ok(id)
}

async fn smoke(runtime: &Path, data: &Path) -> Result<Value, String> {
    if data.exists() {
        return Err("Smoke data directory must be NEW; existing data will not be touched".into());
    }
    let source = data.join("synthetic-source");
    fs::create_dir_all(&source).map_err(|e| e.to_string())?;
    let bytes: Vec<u8> = (0..PNG.len())
        .step_by(2)
        .map(|index| u8::from_str_radix(&PNG[index..index + 2], 16).unwrap())
        .collect();
    let image = source.join("synthetic.png");
    fs::write(&image, &bytes).map_err(|e| e.to_string())?;
    let modified = fs::metadata(&image)
        .and_then(|m| m.modified())
        .map_err(|e| e.to_string())?;
    let source = source.canonicalize().map_err(|e| e.to_string())?;
    // canonicalize() uses the extended Windows prefix; Java's catalog stores normal paths.
    let source = PathBuf::from(
        source
            .to_string_lossy()
            .strip_prefix(r"\\?\")
            .unwrap_or(&source.to_string_lossy())
            .to_string(),
    );
    let mut package_install_ms = None;
    let local_package = argument("--ai-package-zip").ok();
    let download_package = std::env::args().any(|arg| arg == "--download-ai-package");
    if local_package.is_some() || download_package {
        let spec = super::packages::catalog_for_command("ai-cpu")?;
        let install_data = data.to_path_buf();
        let started = Instant::now();
        tauri::async_runtime::spawn_blocking(move || {
            super::packages::PackageManager::default().install(
                &spec,
                &install_data,
                local_package.as_deref(),
            )
        })
        .await
        .map_err(|_| "Package check worker stopped")??;
        package_install_ms = Some(started.elapsed().as_millis() as u64);
    }
    let mut owner = processes::start(runtime, data, |_, _| {}).await?;
    let include_analysis = !std::env::args().any(|arg| arg == "--catalog-only");
    if include_analysis {
        owner.start_analysis(|| false).await?;
    }
    let result = exercise(
        owner.backend.as_ref().ok_or("Backend not ready")?,
        &source,
        include_analysis,
    )
    .await;
    owner.stop().await;
    let id = result?;
    drop(owner);
    let mut restarted = processes::start(runtime, data, |_, _| {}).await?;
    let result = if include_analysis {
        request(
            restarted.backend.as_ref().ok_or("Restart failed")?,
            "GET",
            &format!("/api/catalog/files/{id}/analysis"),
            None,
        )
        .await
    } else {
        restarted
            .backend
            .as_ref()
            .ok_or("Restart failed")?
            .request(
                "GET",
                &format!("/api/catalog/files/{id}/thumbnail"),
                None,
                6 * 1024 * 1024,
            )
            .await
            .and_then(|(status, bytes)| {
                if status == 200 && bytes.starts_with(b"\x89PNG") {
                    Ok(json!({}))
                } else {
                    Err("Catalog was not persisted".into())
                }
            })
    };
    restarted.stop().await;
    let saved = result?;
    if include_analysis && saved["result"]["source_sha256"] != HASH {
        return Err("Analysis was not persisted across restart".into());
    }
    if fs::read(&image).map_err(|e| e.to_string())? != bytes
        || fs::metadata(&image)
            .and_then(|m| m.modified())
            .map_err(|e| e.to_string())?
            != modified
    {
        return Err("Source image changed".into());
    }
    Ok(
        json!({"passed": true, "bundledRuntimes": true, "bundledAiPresent": runtime.join("python/python.exe").is_file(), "catalogOnly": !include_analysis, "batchAnalysis": include_analysis, "scan": true, "ocr": include_analysis, "visual": include_analysis, "thumbnail": true, "persistedAfterRestart": true, "sourceUnchanged": true, "packageInstalled": package_install_ms.is_some(), "packageInstallMs": package_install_ms, "packageDownloaded": download_package}),
    )
}

fn finish_report(data: &Path, result: Result<Value, String>, tauri_resolver: bool) -> i32 {
    let mut report = match &result {
        Ok(value) => value.clone(),
        Err(error) => json!({"passed": false, "error": error}),
    };
    report["tauriResourceResolver"] = Value::Bool(tauri_resolver);
    // The caller chooses an isolated output path; no credentials or user images appear in the report.
    if fs::write(
        data.with_extension("report.json"),
        serde_json::to_vec_pretty(&report).unwrap(),
    )
    .is_err()
    {
        return 2;
    }
    if result.is_ok() {
        0
    } else {
        eprintln!("{report}");
        1
    }
}

pub async fn run_smoke() -> i32 {
    let (runtime, data) = match (argument("--runtime-dir"), argument("--data-dir")) {
        (Ok(runtime), Ok(data)) => (runtime, data),
        _ => return 2,
    };
    let result = smoke(&runtime, &data).await;
    finish_report(&data, result, false)
}

/// Use the real Tauri PathResolver, not a CLI-supplied approximation of its result.
/// Only the data directory is overridden, and it must be new. No webview/user data.
pub fn launch_startup_check(app: tauri::AppHandle) {
    tauri::async_runtime::spawn(async move {
        let data = match argument("--data-dir") {
            Ok(path) => path,
            Err(_) => {
                app.exit(2);
                return;
            }
        };
        let result = match super::runtime_path(&app) {
            Ok(runtime) => smoke(&runtime, &data).await.map(|mut report| {
                report["resourcePathHadVerbatimPrefix"] = Value::Bool(
                    runtime
                        .as_os_str()
                        .as_encoded_bytes()
                        .starts_with(b"\\\\?\\"),
                );
                report
            }),
            Err(error) => Err(error),
        };
        app.exit(finish_report(&data, result, true));
    });
}
