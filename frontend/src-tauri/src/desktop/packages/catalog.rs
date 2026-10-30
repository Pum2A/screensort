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
    /// "python" runs the OCR + caption package; "qwen" runs llama-server with a vision model.
    #[serde(default = "python_kind")]
    pub kind: String,
    /// Label the backend stores with each result, e.g. "qwen3.5-4b". Only for "qwen".
    #[serde(default)]
    pub model: Option<String>,
    /// Files downloaded next to the ZIP's contents, each pinned by its own SHA-256 (model weights
    /// are larger than a release asset may be).
    #[serde(default)]
    pub files: Vec<FileSpec>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct FileSpec {
    pub path: String,
    pub url: String,
    pub sha256: String,
    pub bytes: u64,
}

/// What one download must be: where it comes from, its exact size and SHA-256.
pub struct Source<'a> {
    pub url: Option<&'a str>,
    pub sha256: &'a str,
    pub bytes: u64,
}

fn python_kind() -> String {
    "python".into()
}

fn sha256_hex(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase())
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
            || !sha256_hex(&self.sha256)
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
        let model_label = |s: &str| {
            !s.is_empty()
                && s.len() <= 40
                && s.bytes()
                    .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'-' || c == b'.')
        };
        let valid_kind = match self.kind.as_str() {
            "python" => self.model.is_none() && self.files.is_empty(),
            "qwen" => self.model.as_deref().is_some_and(model_label) && !self.files.is_empty(),
            _ => false,
        };
        if !valid_kind || self.files.len() > 8 {
            return Err("Nieprawidłowe metadane zaufanego pakietu.".into());
        }
        for file in &self.files {
            if !sha256_hex(&file.sha256) || file.bytes == 0 || file.bytes > 4 * 1024 * 1024 * 1024 {
                return Err("Nieprawidłowe metadane zaufanego pakietu.".into());
            }
            portable_path(&file.path)?;
            file_url(&file.url)?;
        }
        Ok(())
    }

    pub fn archive(&self) -> Source<'_> {
        Source {
            url: self.url.as_deref(),
            sha256: &self.sha256,
            bytes: self.archive_bytes,
        }
    }

    pub fn files_bytes(&self) -> u64 {
        self.files.iter().map(|file| file.bytes).sum()
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

impl FileSpec {
    pub fn source(&self) -> Source<'_> {
        Source {
            url: Some(&self.url),
            sha256: &self.sha256,
            bytes: self.bytes,
        }
    }
}

fn plain_https(url: &reqwest::Url, host: &str) -> bool {
    url.scheme() == "https"
        && url.host_str() == Some(host)
        && url.port_or_known_default() == Some(443)
        && url.username().is_empty()
        && url.password().is_none()
        && url.query().is_none()
        && url.fragment().is_none()
}

/// A ZIP from a ScreenSort release, or the official llama.cpp server build.
pub fn download_url(value: &str) -> Result<reqwest::Url, String> {
    let url = reqwest::Url::parse(value).map_err(|_| "Nieprawidłowy adres pakietu.")?;
    let path = url.path();
    if !plain_https(&url, "github.com")
        || !(path.starts_with("/Pum2A/screensort-releases/releases/download/")
            || path.starts_with("/ggml-org/llama.cpp/releases/download/"))
        || !path.ends_with(".zip")
    {
        return Err("Pakiet musi pochodzić z zaufanego wydania przez HTTPS.".into());
    }
    Ok(url)
}

/// A model file from Hugging Face, pinned to one 40-character commit, never a moving branch.
pub fn file_url(value: &str) -> Result<reqwest::Url, String> {
    let url = reqwest::Url::parse(value).map_err(|_| "Nieprawidłowy adres modelu.")?;
    let parts: Vec<&str> = url.path().trim_start_matches('/').split('/').collect();
    let safe = |s: &str| {
        !s.is_empty()
            && s.bytes()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, b'-' | b'_' | b'.'))
            && !s.starts_with('.')
    };
    if !plain_https(&url, "huggingface.co")
        || parts.len() != 5
        || !safe(parts[0])
        || !safe(parts[1])
        || parts[2] != "resolve"
        || parts[3].len() != 40
        || !parts[3]
            .bytes()
            .all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase())
        || !safe(parts[4])
        || !parts[4].ends_with(".gguf")
    {
        return Err("Model musi pochodzić z przypiętej wersji Hugging Face przez HTTPS.".into());
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
