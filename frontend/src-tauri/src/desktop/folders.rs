//! Windows folders where screenshots usually land, offered on the first start.
use serde::Serialize;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager};

/// Counting stops here: the first-start card only needs "how many, roughly".
const COUNT_LIMIT: u32 = 10_000;

#[derive(Serialize, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct KnownFolder {
    /// "screenshots", "downloads" or "desktop"; the interface names them in Polish.
    pub kind: &'static str,
    pub path: String,
    pub images: u32,
}

/// Existing folders only, with the number of PNG/JPG files directly inside (not in subfolders).
#[tauri::command]
pub async fn known_image_folders(app: AppHandle) -> Vec<KnownFolder> {
    let paths = app.path();
    let candidates: Vec<(&'static str, Option<PathBuf>)> = vec![
        (
            "screenshots",
            paths.picture_dir().ok().map(|dir| dir.join("Screenshots")),
        ),
        ("downloads", paths.download_dir().ok()),
        ("desktop", paths.desktop_dir().ok()),
    ];
    tauri::async_runtime::spawn_blocking(move || {
        candidates
            .into_iter()
            .filter_map(|(kind, path)| {
                let path = path?;
                path.is_dir().then(|| KnownFolder {
                    kind,
                    images: count_images(&path),
                    path: path.to_string_lossy().into_owned(),
                })
            })
            .collect()
    })
    .await
    .unwrap_or_default()
}

fn count_images(folder: &Path) -> u32 {
    let Ok(entries) = std::fs::read_dir(folder) else {
        return 0;
    };
    let mut count = 0;
    for entry in entries.flatten() {
        if count >= COUNT_LIMIT {
            break;
        }
        let image = entry.file_type().is_ok_and(|kind| kind.is_file())
            && entry
                .path()
                .extension()
                .and_then(|extension| extension.to_str())
                .is_some_and(|extension| {
                    matches!(
                        extension.to_ascii_lowercase().as_str(),
                        "png" | "jpg" | "jpeg"
                    )
                });
        if image {
            count += 1;
        }
    }
    count
}

#[cfg(test)]
mod tests {
    use super::count_images;

    #[test]
    fn counts_only_png_and_jpg_files_directly_in_the_folder() {
        let folder = tempfile::tempdir().unwrap();
        for name in ["a.png", "b.JPG", "c.jpeg", "notes.txt", "d.gif"] {
            std::fs::write(folder.path().join(name), b"x").unwrap();
        }
        std::fs::create_dir(folder.path().join("inner.png")).unwrap();
        std::fs::write(folder.path().join("inner.png").join("e.png"), b"x").unwrap();
        assert_eq!(count_images(folder.path()), 3);
        assert_eq!(count_images(&folder.path().join("missing")), 0);
    }
}
