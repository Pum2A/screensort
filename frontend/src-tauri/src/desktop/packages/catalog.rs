//! Trust comes from the catalog compiled into this application, not from a downloaded ZIP.
use serde::{Deserialize, Serialize};
use std::path::{Component, Path, PathBuf};

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct PackageSpec {
    pub id: String,
    pub name: String,
    pub version: String,
    pub url: Option<String>,
    pub sha256: String,
    pub archive_bytes: u64,
    pub unpacked_bytes: u64,
    pub file_count: usize,
    pub required_files: Vec<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Catalog {
    schema_version: u32,
    packages: Vec<PackageSpec>,
}

pub fn catalog() -> Result<Vec<PackageSpec>, String> {
    let catalog: Catalog = serde_json::from_str(include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../packaging/windows/ai-packages.json"
    )))
    .map_err(|_| "Nieprawidłowy katalog pakietów w tej wersji aplikacji.")?;
    if catalog.schema_version != 1 || catalog.packages.len() > 10 {
        return Err("Nieobsługiwana wersja katalogu pakietów.".into());
    }
    let mut ids = std::collections::HashSet::new();
    for spec in &catalog.packages {
        spec.validate()?;
        if !ids.insert(&spec.id) {
            return Err("Powtórzone ID pakietu.".into());
        }
    }
    Ok(catalog.packages)
}

impl PackageSpec {
    pub fn validate(&self) -> Result<(), String> {
        let safe_id = |s: &str| {
            !s.is_empty()
                && s.len() <= 64
                && s.bytes()
                    .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'-')
        };
        if !safe_id(&self.id)
            || !safe_id(&self.version)
            || self.name.len() > 120
            || self.sha256.len() != 64
            || !self
                .sha256
                .bytes()
                .all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase())
            || self.archive_bytes == 0
            || self.archive_bytes > 2 * 1024 * 1024 * 1024
            || self.unpacked_bytes == 0
            || self.unpacked_bytes > 8 * 1024 * 1024 * 1024
            || self.file_count == 0
            || self.file_count > 100_000
            || self.required_files.is_empty()
            || self.required_files.len() > 32
        {
            return Err("Nieprawidłowe metadane zaufanego pakietu.".into());
        }
        for required in &self.required_files {
            portable_path(required)?;
        }
        if let Some(url) = &self.url {
            download_url(url)?;
        }
        Ok(())
    }

    pub fn directory(&self, data: &Path) -> PathBuf {
        data.join("ai-packages").join(format!(
            "{}-{}-{}",
            self.id,
            self.version,
            &self.sha256[..16]
        ))
    }
}

pub fn download_url(value: &str) -> Result<reqwest::Url, String> {
    let url = reqwest::Url::parse(value).map_err(|_| "Nieprawidłowy adres pakietu.")?;
    if url.scheme() != "https"
        || url.host_str() != Some("github.com")
        || url.port_or_known_default() != Some(443)
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
        || !url
            .path()
            .starts_with("/Pum2A/screensort-releases/releases/download/")
        || !url.path().ends_with(".zip")
    {
        return Err("Pakiet musi pochodzić z zaufanego wydania ScreenSort przez HTTPS.".into());
    }
    Ok(url)
}

/// Reject Windows aliases as well as traversal; do not silently rewrite an archive name.
pub fn portable_path(name: &str) -> Result<PathBuf, String> {
    if name.is_empty() || name.len() > 240 || name.contains('\\') || name.starts_with('/') {
        return Err("Pakiet zawiera niedozwoloną ścieżkę.".into());
    }
    for part in name.strip_suffix('/').unwrap_or(name).split('/') {
        let base = part.split('.').next().unwrap_or("").to_ascii_uppercase();
        let reserved = matches!(
            base.as_str(),
            "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$"
        ) || (base.len() == 4
            && (base.starts_with("COM") || base.starts_with("LPT"))
            && matches!(base.as_bytes()[3], b'1'..=b'9'))
            || ["COM¹", "COM²", "COM³", "LPT¹", "LPT²", "LPT³"].contains(&base.as_str());
        if part.is_empty()
            || part == "."
            || part == ".."
            || part.ends_with(['.', ' '])
            || reserved
            || part
                .chars()
                .any(|c| c.is_control() || "<>:\"|?*".contains(c))
        {
            return Err("Pakiet zawiera niedozwoloną nazwę Windows.".into());
        }
    }
    let path = PathBuf::from(name);
    if path
        .components()
        .any(|c| !matches!(c, Component::Normal(_)))
    {
        return Err("Pakiet próbuje wyjść poza własny katalog.".into());
    }
    Ok(path)
}
