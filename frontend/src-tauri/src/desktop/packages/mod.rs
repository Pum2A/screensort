//! Optional, immutable AI installations. Only catalog-pinned packages can execute.
mod archive;
mod catalog;
mod download;

pub use catalog::PackageSpec;
use serde::{Deserialize, Serialize};
use std::{
    fs::{self, File, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
};

#[derive(Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub phase: String,
    pub busy: bool,
    pub completed_bytes: u64,
    pub total_bytes: u64,
    pub error: Option<String>,
}

#[derive(Default)]
pub struct PackageManager {
    progress: Mutex<Progress>,
    cancel: AtomicBool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PackageView {
    pub package: PackageSpec,
    pub installed: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PackageStatus {
    pub packages: Vec<PackageView>,
    pub bundled: bool,
    pub progress: Progress,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Receipt {
    id: String,
    version: String,
    sha256: String,
}

pub fn cancelled_message() -> String {
    archive::CANCELLED.into()
}

pub fn catalog_for_command(id: &str) -> Result<PackageSpec, String> {
    catalog::catalog()?
        .into_iter()
        .find(|spec| spec.id == id)
        .ok_or("Nieznany pakiet AI.".into())
}

impl PackageManager {
    pub fn busy(&self) -> bool {
        self.progress.lock().expect("package lock").busy
    }

    pub fn begin(&self) -> Result<(), String> {
        let mut state = self.progress.lock().map_err(|_| "Brak stanu instalacji.")?;
        if state.busy {
            return Err("Instalacja pakietu jest już w toku.".into());
        }
        self.cancel.store(false, Ordering::SeqCst);
        *state = Progress {
            phase: "PREPARING".into(),
            busy: true,
            ..Progress::default()
        };
        Ok(())
    }

    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::SeqCst);
    }
    fn cancelled(&self) -> bool {
        self.cancel.load(Ordering::SeqCst)
    }

    fn update(&self, phase: &str, completed: u64, total: u64) {
        let mut state = self.progress.lock().expect("package lock");
        state.phase = phase.into();
        state.completed_bytes = completed;
        state.total_bytes = total;
    }

    pub fn finish(&self, result: Result<(), String>) {
        let mut state = self.progress.lock().expect("package lock");
        state.busy = false;
        state.phase = match &result {
            Ok(_) => "INSTALLED",
            Err(e) if e == archive::CANCELLED => "CANCELLED",
            _ => "FAILED",
        }
        .into();
        state.error = result.err();
    }

    pub fn status(&self, data: &Path, bundled: &Path) -> Result<PackageStatus, String> {
        Ok(PackageStatus {
            packages: catalog::catalog()?
                .into_iter()
                .map(|package| {
                    let installed = installed(&package, data);
                    PackageView { package, installed }
                })
                .collect(),
            bundled: bundled.join("python/python.exe").is_file(),
            progress: self
                .progress
                .lock()
                .map_err(|_| "Brak stanu instalacji.")?
                .clone(),
        })
    }

    /// Called on a blocking worker, never on the WebView/event loop thread.
    pub fn install(
        &self,
        spec: &PackageSpec,
        data: &Path,
        local: Option<&Path>,
    ) -> Result<(), String> {
        spec.validate()?;
        let base = data.join("ai-packages");
        archive::plain_directory(&base)?;
        if installed(spec, data) {
            return Ok(());
        }
        let target = spec.directory(data);
        if target
            .try_exists()
            .map_err(|_| "Nie można sprawdzić katalogu pakietu.")?
        {
            return Err("Katalog tej wersji istnieje, ale nie przeszedł kontroli. Nie nadpisano go; sprawdź instalację.".into());
        }
        // ZIP and extracted payload coexist until activation; keep a safety margin.
        check_space(
            &base,
            spec.archive_bytes + spec.unpacked_bytes + 256 * 1024 * 1024,
        )?;
        let mut zip = tempfile::NamedTempFile::new_in(&base)
            .map_err(|_| "Nie można utworzyć pliku tymczasowego pakietu.")?;
        if let Some(path) = local {
            self.update("VERIFYING", 0, spec.archive_bytes);
            let mut source = File::open(path).map_err(|_| "Nie można otworzyć wybranego ZIP.")?;
            if source
                .metadata()
                .map_err(|_| "Nie można sprawdzić ZIP.")?
                .len()
                != spec.archive_bytes
            {
                return Err(
                    "Wybrany plik nie jest pakietem wymaganym przez tę wersję ScreenSort.".into(),
                );
            }
            archive::copy_verified(
                &mut source,
                zip.as_file_mut(),
                spec,
                &|| self.cancelled(),
                |n| self.update("VERIFYING", n, spec.archive_bytes),
            )?;
        } else {
            self.update("DOWNLOADING", 0, spec.archive_bytes);
            tauri::async_runtime::block_on(download::download(
                spec,
                zip.as_file_mut(),
                &|| self.cancelled(),
                |n| self.update("DOWNLOADING", n, spec.archive_bytes),
            ))?;
        }
        let stage = tempfile::Builder::new()
            .prefix("install-")
            .tempdir_in(&base)
            .map_err(|_| "Nie można przygotować katalogu instalacji.")?;
        self.update("EXTRACTING", 0, spec.unpacked_bytes);
        archive::extract_verified(
            zip.as_file_mut(),
            stage.path(),
            spec,
            &|| self.cancelled(),
            |n| self.update("EXTRACTING", n, spec.unpacked_bytes),
        )?;
        archive::cancelled(&|| self.cancelled())?;
        let receipt = Receipt {
            id: spec.id.clone(),
            version: spec.version.clone(),
            sha256: spec.sha256.clone(),
        };
        let mut marker = OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(stage.path().join("installed.json"))
            .map_err(|_| "Nie można zakończyć instalacji pakietu.")?;
        marker
            .write_all(&serde_json::to_vec(&receipt).map_err(|_| "Błąd potwierdzenia instalacji.")?)
            .and_then(|_| marker.sync_all())
            .map_err(|_| "Nie można zapisać potwierdzenia instalacji.")?;
        drop(marker);
        // Same-volume rename makes a complete version visible in one step. No working version is overwritten.
        fs::rename(stage.path(), &target).map_err(|_| {
            "Nie można aktywować pakietu. Dotychczasowe wersje pozostają bez zmian."
        })?;
        self.update("INSTALLED", spec.unpacked_bytes, spec.unpacked_bytes);
        Ok(())
    }
}

fn installed(spec: &PackageSpec, data: &Path) -> bool {
    let root = spec.directory(data);
    if !archive::ordinary(&root, false) {
        return false;
    }
    let marker = root.join("installed.json");
    if !archive::ordinary(&marker, true) {
        return false;
    }
    let Ok(file) = File::open(marker) else {
        return false;
    };
    if file.metadata().map_or(true, |m| m.len() > 4096) {
        return false;
    }
    let Ok(receipt) = serde_json::from_reader::<_, Receipt>(file) else {
        return false;
    };
    receipt.id == spec.id
        && receipt.version == spec.version
        && receipt.sha256 == spec.sha256
        && spec.required_files.iter().all(|name| {
            let path = root.join(name);
            archive::ordinary(&path, true) && archive::ordinary_ancestors(&path, &root)
        })
}

pub fn runtime(data: &Path, bundled: &Path) -> Result<PathBuf, String> {
    for spec in catalog::catalog()? {
        if installed(&spec, data) {
            return Ok(spec.directory(data));
        }
    }
    if bundled.join("python/python.exe").is_file() {
        return Ok(bundled.to_path_buf());
    }
    Err("Pakiet AI nie jest zainstalowany. Pobierz go lub wybierz jego ZIP w sekcji Pakiet AI. Katalog działa bez niego.".into())
}

fn check_space(path: &Path, needed: u64) -> Result<(), String> {
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        let wide: Vec<u16> = path.as_os_str().encode_wide().chain(Some(0)).collect();
        let mut available = 0;
        // Windows writes only to this local u64; path is NUL-terminated for the call.
        let ok = unsafe {
            windows_sys::Win32::Storage::FileSystem::GetDiskFreeSpaceExW(
                wide.as_ptr(),
                &mut available,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        };
        if ok == 0 {
            return Err("Nie można sprawdzić wolnego miejsca na pakiet.".into());
        }
        if available < needed {
            return Err("Za mało wolnego miejsca. Potrzebne jest miejsce na ZIP, rozpakowany pakiet i zapas 256 MiB.".into());
        }
    }
    #[cfg(not(windows))]
    let _ = (path, needed);
    Ok(())
}

#[cfg(test)]
mod tests;
