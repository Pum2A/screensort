//! Normalize only our runtime/data paths at the external-process boundary.
use std::path::{Path, PathBuf};

pub fn process_path(path: &Path) -> Result<PathBuf, String> {
    // No string slicing: reserved names, UNC and long paths must not change meaning.
    let compatible = dunce::simplified(path);
    #[cfg(windows)]
    {
        use std::path::{Component, Prefix};
        if matches!(compatible.components().next(), Some(Component::Prefix(prefix))
            if matches!(prefix.kind(), Prefix::VerbatimDisk(_) | Prefix::VerbatimUNC(_, _)
                | Prefix::Verbatim(_) | Prefix::DeviceNS(_)))
        {
            return Err("Ścieżka instalacji lub danych wymaga formatu nieobsługiwanego przez lokalne narzędzia. Użyj krótszej, zwykłej ścieżki Windows. Nie usuwaj danych.".into());
        }
    }
    Ok(compatible.to_path_buf())
}

#[cfg(test)]
mod tests {
    use super::process_path;
    use std::path::{Path, PathBuf};

    #[test]
    fn ordinary_paths_are_unchanged() {
        let path = Path::new("runtime/models");
        assert_eq!(process_path(path).unwrap(), path);
    }

    #[cfg(windows)]
    #[test]
    fn tauri_verbatim_disk_path_becomes_a_compatible_path_without_io() {
        let path = Path::new(r"\\?\C:\Users\tester\AppData\Local\ScreenSort\runtime");
        assert_eq!(
            process_path(path).unwrap(),
            PathBuf::from(r"C:\Users\tester\AppData\Local\ScreenSort\runtime")
        );
    }

    #[cfg(windows)]
    #[test]
    fn spaces_and_unicode_are_preserved() {
        let path = Path::new(r"\\?\C:\Users\Zażółć Gęślą\ScreenSort\runtime");
        assert_eq!(
            process_path(path).unwrap(),
            PathBuf::from(r"C:\Users\Zażółć Gęślą\ScreenSort\runtime")
        );
    }

    #[cfg(windows)]
    #[test]
    fn ordinary_network_paths_are_not_rewritten() {
        let path = Path::new(r"\\server\share\ScreenSort");
        assert_eq!(process_path(path).unwrap(), path);
    }

    #[cfg(windows)]
    #[test]
    fn unsupported_verbatim_paths_fail_instead_of_becoming_another_path() {
        for path in [
            r"\\?\UNC\server\share\runtime",
            r"\\?\C:\CON\runtime",
            r"\\?\C:\folder.\runtime",
            r"\\.\PhysicalDrive0",
        ] {
            assert!(process_path(Path::new(path)).is_err(), "{path}");
        }
        let long = format!(r"\\?\C:\{}\runtime", "folder\\".repeat(50));
        assert!(process_path(Path::new(&long)).is_err());
    }
}
