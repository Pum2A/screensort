use super::catalog::{portable_path, PackageSpec};
use sha2::{Digest, Sha256};
use std::{
    collections::HashSet,
    fs::{self, File, OpenOptions},
    io::{Read, Seek, SeekFrom, Write},
    path::Path,
};

const BUFFER: usize = 128 * 1024;
pub const CANCELLED: &str = "Anulowano instalację. Dotychczasowy pakiet pozostaje bez zmian.";

pub fn cancelled(check: &impl Fn() -> bool) -> Result<(), String> {
    if check() {
        Err(CANCELLED.into())
    } else {
        Ok(())
    }
}

fn link(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        if metadata.file_attributes() & 0x400 != 0 {
            return true;
        }
    }
    metadata.file_type().is_symlink()
}

pub fn ordinary(path: &Path, file: bool) -> bool {
    fs::symlink_metadata(path)
        .is_ok_and(|m| !link(&m) && if file { m.is_file() } else { m.is_dir() })
}

pub fn ordinary_ancestors(path: &Path, root: &Path) -> bool {
    path.ancestors()
        .skip(1)
        .take_while(|p| *p != root)
        .all(|p| ordinary(p, false))
        && root.ancestors().all(|p| ordinary(p, false))
}

/// Check ancestors before creating anything: existing junctions are not installation roots.
pub fn plain_directory(path: &Path) -> Result<(), String> {
    if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
        plain_directory(parent)?;
    }
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_dir() && !link(&metadata) => Ok(()),
        Ok(_) => Err("Katalog pakietu jest przekierowany lub nie jest zwykłym folderem.".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            fs::create_dir(path).map_err(|_| {
                "Nie można utworzyć katalogu pakietów. Sprawdź wolne miejsce i uprawnienia."
                    .to_string()
            })
        }
        Err(_) => Err("Nie można sprawdzić katalogu pakietów.".into()),
    }
}

// Check each directory once within fresh staging, not ten ancestors for every file.
fn extraction_directory(
    path: &Path,
    known: &mut HashSet<std::path::PathBuf>,
) -> Result<(), String> {
    if known.contains(path) {
        return Ok(());
    }
    extraction_directory(
        path.parent().ok_or("Nieprawidłowy katalog archiwum.")?,
        known,
    )?;
    match fs::symlink_metadata(path) {
        Ok(m) if m.is_dir() && !link(&m) => {}
        Ok(_) => return Err("Nieprawidłowy katalog wewnątrz pakietu.".into()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            fs::create_dir(path).map_err(|_| "Nie można utworzyć katalogu wewnątrz pakietu.")?;
        }
        Err(_) => return Err("Nie można sprawdzić katalogu wewnątrz pakietu.".into()),
    }
    known.insert(path.to_path_buf());
    Ok(())
}

/// Stage and hash exactly the same bytes that will later be extracted. Bounded memory.
pub fn copy_verified(
    input: &mut impl Read,
    output: &mut File,
    spec: &PackageSpec,
    check: &impl Fn() -> bool,
    mut progress: impl FnMut(u64),
) -> Result<(), String> {
    let mut buffer = vec![0; BUFFER];
    let mut digest = Sha256::new();
    let mut copied = 0_u64;
    loop {
        cancelled(check)?;
        let count = input
            .read(&mut buffer)
            .map_err(|_| "Przerwany odczyt pakietu. Ponów instalację.")?;
        if count == 0 {
            break;
        }
        copied += count as u64;
        if copied > spec.archive_bytes {
            return Err("Pakiet jest większy niż zaufana wersja.".into());
        }
        output
            .write_all(&buffer[..count])
            .map_err(|_| "Nie można zapisać pakietu. Sprawdź wolne miejsce.")?;
        digest.update(&buffer[..count]);
        progress(copied);
    }
    if copied != spec.archive_bytes || format!("{:x}", digest.finalize()) != spec.sha256 {
        return Err(
            "Nie zgadza się rozmiar lub SHA-256 pakietu. Niczego nie zainstalowano.".into(),
        );
    }
    output
        .flush()
        .map_err(|_| "Nie można zakończyć zapisu pakietu.")?;
    Ok(())
}

pub fn extract_verified(
    input: &mut File,
    stage: &Path,
    spec: &PackageSpec,
    check: &impl Fn() -> bool,
    mut progress: impl FnMut(u64),
) -> Result<(), String> {
    input
        .seek(SeekFrom::Start(0))
        .map_err(|_| "Nie można otworzyć pobranego pakietu.")?;
    let mut zip = zip::ZipArchive::new(input).map_err(|_| "Nieprawidłowe archiwum pakietu.")?;
    if zip.len() > 100_000 {
        return Err("Archiwum zawiera zbyt wiele wpisów.".into());
    }
    let mut names = HashSet::new();
    if !ordinary(stage, false) {
        return Err("Nieprawidłowy katalog tymczasowy.".into());
    }
    let mut directories = HashSet::from([stage.to_path_buf()]);
    let mut bytes = 0_u64;
    let mut count = 0_usize;
    let mut buffer = vec![0; BUFFER];
    for index in 0..zip.len() {
        cancelled(check)?;
        let mut entry = zip
            .by_index(index)
            .map_err(|_| "Nie można odczytać wpisu archiwum.")?;
        let name = entry
            .name()
            .map_err(|_| "Nieprawidłowa nazwa w archiwum.")?
            .into_owned();
        let path = portable_path(&name)?;
        if entry.enclosed_name().is_none()
            || entry
                .unix_mode()
                .is_some_and(|mode| !matches!(mode & 0o170000, 0 | 0o040000 | 0o100000))
        {
            return Err("Pakiet zawiera dowiązanie lub niedozwoloną ścieżkę.".into());
        }
        let first = name.split('/').next().unwrap_or("");
        if !matches!(
            first,
            "python"
                | "models"
                | "THIRD-PARTY-NOTICES.md"
                | "FLORENCE-LICENSE.txt"
                | "runtime-manifest.json"
        ) {
            return Err("Archiwum próbuje instalować pliki spoza pakietu AI.".into());
        }
        if !names.insert(name.trim_end_matches('/').to_ascii_lowercase()) {
            return Err("Archiwum zawiera kolidujące nazwy plików.".into());
        }
        let target = stage.join(path);
        if entry.is_dir() {
            extraction_directory(&target, &mut directories)?;
            continue;
        }
        if entry.size() > 1024 * 1024 * 1024
            || entry.size() > spec.unpacked_bytes.saturating_sub(bytes)
        {
            return Err("Przekroczono limit rozpakowania pakietu.".into());
        }
        extraction_directory(
            target.parent().ok_or("Nieprawidłowy katalog pakietu.")?,
            &mut directories,
        )?;
        let mut output = OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(&target)
            .map_err(|_| "Nie można utworzyć pliku pakietu. Sprawdź wolne miejsce.")?;
        let mut extracted = 0_u64;
        loop {
            cancelled(check)?;
            let n = entry
                .read(&mut buffer)
                .map_err(|_| "Uszkodzony wpis lub CRC archiwum.")?;
            if n == 0 {
                break;
            }
            extracted += n as u64;
            if extracted > entry.size() {
                return Err("Wpis archiwum przekroczył deklarowany rozmiar.".into());
            }
            output
                .write_all(&buffer[..n])
                .map_err(|_| "Brak miejsca lub błąd zapisu pliku pakietu.")?;
            progress(bytes + extracted);
        }
        if extracted != entry.size() {
            return Err("Niepełny plik w archiwum.".into());
        }
        bytes += extracted;
        count += 1;
    }
    if count != spec.file_count || bytes != spec.unpacked_bytes {
        return Err("Zawartość archiwum nie zgadza się z katalogiem pakietów.".into());
    }
    // Separately downloaded model files arrive after extraction; `installed` checks them later.
    for name in spec
        .required_files
        .iter()
        .filter(|name| !spec.files.iter().any(|file| &file.path == *name))
    {
        let metadata = fs::symlink_metadata(stage.join(portable_path(name)?))
            .map_err(|_| "Pakiet nie zawiera wymaganych plików.")?;
        if !metadata.is_file() || link(&metadata) {
            return Err("Nieprawidłowy wymagany plik pakietu.".into());
        }
    }
    Ok(())
}
