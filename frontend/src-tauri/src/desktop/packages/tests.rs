use super::*;
use sha2::{Digest, Sha256};
use std::io::{Cursor, Read};
use zip::{write::SimpleFileOptions, ZipWriter};

fn fixture(entries: &[(&str, &[u8])]) -> (tempfile::NamedTempFile, PackageSpec) {
    let mut input = tempfile::NamedTempFile::new().unwrap();
    {
        let mut zip = ZipWriter::new(input.as_file_mut());
        for (name, data) in entries {
            zip.start_file(*name, SimpleFileOptions::default()).unwrap();
            zip.write_all(data).unwrap();
        }
        zip.finish().unwrap();
    }
    let bytes = fs::read(input.path()).unwrap();
    let spec = PackageSpec {
        id: "test".into(),
        name: "Fixture".into(),
        version: "1".into(),
        url: None,
        sha256: format!("{:x}", Sha256::digest(&bytes)),
        archive_bytes: bytes.len() as u64,
        unpacked_bytes: entries.iter().map(|(_, data)| data.len() as u64).sum(),
        file_count: entries.len(),
        required_files: vec!["python/python.exe".into()],
        kind: "python".into(),
        model: None,
        files: Vec::new(),
    };
    (input, spec)
}

#[test]
fn installs_and_recognizes_only_a_complete_version() {
    let (input, spec) = fixture(&[("python/python.exe", b"fixture-not-an-executable")]);
    let data = tempfile::tempdir().unwrap();
    let manager = PackageManager::default();
    manager.begin().unwrap();
    assert!(manager.begin().is_err());
    manager
        .install(&spec, data.path(), Some(input.path()))
        .unwrap();
    manager.finish(Ok(()));
    assert!(installed(&spec, data.path()));
    assert!(!manager.busy());
    assert_eq!(
        fs::read_dir(data.path().join("ai-packages"))
            .unwrap()
            .count(),
        1
    );
    fs::write(spec.directory(data.path()).join("installed.json"), b"{}").unwrap();
    assert!(!installed(&spec, data.path()));
    assert!(manager
        .install(&spec, data.path(), Some(input.path()))
        .is_err());
}

#[test]
fn wrong_hash_cannot_change_an_existing_version() {
    let (input, mut spec) = fixture(&[("python/python.exe", b"unchanged")]);
    let data = tempfile::tempdir().unwrap();
    let manager = PackageManager::default();
    manager
        .install(&spec, data.path(), Some(input.path()))
        .unwrap();
    let previous = spec.directory(data.path());
    spec.version = "2".into();
    spec.sha256 = "0".repeat(64);
    assert!(manager
        .install(&spec, data.path(), Some(input.path()))
        .is_err());
    assert_eq!(
        fs::read(previous.join("python/python.exe")).unwrap(),
        b"unchanged"
    );
    assert!(!spec.directory(data.path()).exists());
    assert_eq!(
        fs::read_dir(data.path().join("ai-packages"))
            .unwrap()
            .count(),
        1
    );
}

#[test]
fn rejects_traversal_aliases_and_windows_devices() {
    for name in [
        "../secret",
        "/secret",
        "C:/secret",
        "python/../../x",
        "python/a\\b",
        "python/NUL.txt",
        "python/COM1",
        "python/COM¹.txt",
        "python/LPT³",
        "python/a.",
        "python/a ",
        "python/a:stream",
        "python//a",
        "python//",
    ] {
        assert!(catalog::portable_path(name).is_err(), "{name}");
    }
    assert!(catalog::portable_path("python/Lib/module.py").is_ok());
}

#[test]
fn rejects_unsafe_zip_payloads_and_leaves_no_installation() {
    for entries in [
        vec![("../outside", b"abc".as_slice())],
        vec![("backend.jar", b"abc".as_slice())],
        vec![
            ("python/python.exe", b"abc".as_slice()),
            ("python/PYTHON.exe", b"abc".as_slice()),
        ],
        vec![("python/unrelated", b"abc".as_slice())],
    ] {
        let (input, spec) = fixture(&entries);
        let data = tempfile::tempdir().unwrap();
        assert!(PackageManager::default()
            .install(&spec, data.path(), Some(input.path()))
            .is_err());
        assert_eq!(
            fs::read_dir(data.path().join("ai-packages"))
                .unwrap()
                .count(),
            0
        );
    }
}

#[test]
fn rejects_false_expansion_size_and_count() {
    let (mut input, spec) = fixture(&[("python/python.exe", b"abc")]);
    for (bytes, count) in [(2, 1), (4, 1), (3, 2)] {
        let mut changed = spec.clone();
        changed.unpacked_bytes = bytes;
        changed.file_count = count;
        let stage = tempfile::tempdir().unwrap();
        assert!(archive::extract_verified(
            input.as_file_mut(),
            stage.path(),
            &changed,
            &|| false,
            |_| {}
        )
        .is_err());
    }
}

#[test]
fn cancelled_copy_and_extraction_never_activate() {
    let (mut input, spec) = fixture(&[("python/python.exe", b"abc")]);
    let mut output = tempfile::tempfile().unwrap();
    let mut source = Cursor::new(fs::read(input.path()).unwrap());
    assert_eq!(
        archive::copy_verified(&mut source, &mut output, &spec, &|| true, |_| {}).unwrap_err(),
        archive::CANCELLED
    );
    let stage = tempfile::tempdir().unwrap();
    assert_eq!(
        archive::extract_verified(input.as_file_mut(), stage.path(), &spec, &|| true, |_| {})
            .unwrap_err(),
        archive::CANCELLED
    );
    let manager = PackageManager::default();
    manager.begin().unwrap();
    manager.cancel();
    let data = tempfile::tempdir().unwrap();
    assert!(manager
        .install(&spec, data.path(), Some(input.path()))
        .is_err());
    assert!(!spec.directory(data.path()).exists());
}

#[test]
fn trusted_catalog_rejects_external_urls_and_invalid_sizes() {
    for url in [
        "http://github.com/Pum2A/screensort-releases/releases/download/v/a.zip",
        "https://evil.test/a.zip",
        "https://github.com/another/repo/releases/download/v/a.zip",
        "https://user@github.com/Pum2A/screensort-releases/releases/download/v/a.zip",
    ] {
        assert!(catalog::download_url(url).is_err());
    }
    assert!(catalog::download_url(
        "https://github.com/Pum2A/screensort-releases/releases/download/v/a.zip"
    )
    .is_ok());
    let (_, mut spec) = fixture(&[("python/python.exe", b"abc")]);
    spec.archive_bytes = 0;
    assert!(spec.validate().is_err());
    assert!(catalog::catalog().is_ok());
}

#[test]
fn verifies_copy_without_loading_the_archive_into_memory() {
    let (input, spec) = fixture(&[("python/python.exe", b"abc")]);
    let mut source = File::open(input.path()).unwrap();
    let mut output = tempfile::tempfile().unwrap();
    archive::copy_verified(&mut source, &mut output, &spec, &|| false, |_| {}).unwrap();
    use std::io::{Seek, SeekFrom};
    output.seek(SeekFrom::Start(0)).unwrap();
    let mut copied = Vec::new();
    output.read_to_end(&mut copied).unwrap();
    assert_eq!(copied, fs::read(input.path()).unwrap());
}

#[test]
fn rejects_archive_symlinks_even_when_the_archive_hash_is_correct() {
    let mut input = tempfile::tempfile().unwrap();
    {
        let mut zip = ZipWriter::new(&mut input);
        zip.add_symlink(
            "python/python.exe",
            "../../outside",
            SimpleFileOptions::default(),
        )
        .unwrap();
        zip.finish().unwrap();
    }
    let (_, spec) = fixture(&[("python/python.exe", b"../../outside")]);
    let stage = tempfile::tempdir().unwrap();
    let error =
        archive::extract_verified(&mut input, stage.path(), &spec, &|| false, |_| {}).unwrap_err();
    assert!(error.contains("dowiązanie"), "{error}");
}

#[test]
fn activation_never_replaces_an_existing_version() {
    let root = tempfile::tempdir().unwrap();
    let stage = root.path().join("stage");
    let target = root.path().join("existing");
    fs::create_dir(&stage).unwrap();
    fs::create_dir(&target).unwrap();
    fs::write(target.join("keep"), b"original").unwrap();
    assert!(activate(&stage, &target, &|| false).is_err());
    assert_eq!(fs::read(target.join("keep")).unwrap(), b"original");
    assert!(stage.exists());
}

#[test]
fn activation_obeys_cancellation_before_rename() {
    let root = tempfile::tempdir().unwrap();
    let stage = root.path().join("stage");
    fs::create_dir(&stage).unwrap();
    assert!(activate(&stage, &root.path().join("target"), &|| true).is_err());
    assert!(stage.exists());
}

#[cfg(windows)]
#[test]
fn activation_retries_a_brief_windows_file_lock() {
    use std::os::windows::fs::OpenOptionsExt;
    let root = tempfile::tempdir().unwrap();
    let stage = root.path().join("stage");
    fs::create_dir(&stage).unwrap();
    fs::write(stage.join("file"), b"payload").unwrap();
    let held = OpenOptions::new()
        .read(true)
        .share_mode(0)
        .open(stage.join("file"))
        .unwrap();
    let release = std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(400));
        drop(held);
    });
    let target = root.path().join("installed");
    activate(&stage, &target, &|| false).unwrap();
    release.join().unwrap();
    assert_eq!(fs::read(target.join("file")).unwrap(), b"payload");
}

#[test]
fn qwen_packages_pin_the_llama_build_and_each_model_file() {
    let catalog = catalog::catalog().unwrap();
    let qwen: Vec<_> = catalog.iter().filter(|spec| spec.kind == "qwen").collect();
    assert_eq!(qwen.len(), 2);
    for spec in qwen {
        assert!(spec
            .model
            .as_deref()
            .is_some_and(|m| m.starts_with("qwen3.5-")));
        assert!(spec
            .required_files
            .contains(&"llama-server.exe".to_string()));
        for path in ["models/qwen.gguf", "models/mmproj.gguf"] {
            assert!(spec.files.iter().any(|file| file.path == path));
            assert!(spec.required_files.contains(&path.to_string()));
        }
    }
    assert!(catalog::download_url(
        "https://github.com/ggml-org/llama.cpp/releases/download/b1/llama-b1-bin-win-vulkan-x64.zip"
    )
    .is_ok());
}

#[test]
fn model_files_must_come_from_a_pinned_hugging_face_commit() {
    let commit = "e87f176479d0855a907a41277aca2f8ee7a09523";
    assert!(catalog::file_url(&format!(
        "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/{commit}/mmproj-F16.gguf"
    ))
    .is_ok());
    for url in [
        "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/mmproj-F16.gguf".to_string(),
        format!("http://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/{commit}/mmproj-F16.gguf"),
        format!("https://evil.test/unsloth/Qwen3.5-4B-GGUF/resolve/{commit}/mmproj-F16.gguf"),
        format!("https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/{commit}/model.exe"),
        format!("https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/{commit}/a.gguf?x=1"),
        format!("https://huggingface.co/unsloth/../resolve/{commit}/a.gguf"),
    ] {
        assert!(catalog::file_url(&url).is_err(), "{url}");
    }
}

#[test]
fn a_package_that_downloads_model_files_cannot_be_installed_from_a_zip() {
    let (input, mut spec) = fixture(&[("llama-server.exe", b"abc")]);
    spec.kind = "qwen".into();
    spec.model = Some("qwen3.5-2b".into());
    spec.files = vec![catalog::FileSpec {
        path: "models/qwen.gguf".into(),
        url: format!(
            "https://huggingface.co/a/b/resolve/{}/qwen.gguf",
            "0".repeat(40)
        ),
        sha256: "0".repeat(64),
        bytes: 3,
    }];
    let data = tempfile::tempdir().unwrap();
    let error = PackageManager::default()
        .install(&spec, data.path(), Some(input.path()))
        .unwrap_err();
    assert!(error.contains("ZIP"), "{error}");
}
