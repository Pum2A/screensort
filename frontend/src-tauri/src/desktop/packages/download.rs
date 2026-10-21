use super::{
    archive::{cancelled, CANCELLED},
    catalog::{download_url, PackageSpec},
};
use sha2::{Digest, Sha256};
use std::{fs::File, io::Write, time::Duration};

fn allowed_redirect(url: &reqwest::Url) -> bool {
    url.scheme() == "https"
        && url.port_or_known_default() == Some(443)
        && url.username().is_empty()
        && url.password().is_none()
        && matches!(
            url.host_str(),
            Some(
                "github.com"
                    | "release-assets.githubusercontent.com"
                    | "objects.githubusercontent.com"
            )
        )
}

pub async fn download(
    spec: &PackageSpec,
    output: &mut File,
    check: &impl Fn() -> bool,
    progress: impl FnMut(u64),
) -> Result<(), String> {
    let url = download_url(
        spec.url
            .as_deref()
            .ok_or("Ta wersja pakietu nie ma jeszcze publicznego adresu pobrania.")?,
    )?;
    let client = reqwest::Client::builder()
        .https_only(true)
        .referer(false)
        .connect_timeout(Duration::from_secs(10))
        .read_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(60 * 45))
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() >= 4 || !allowed_redirect(attempt.url()) {
                attempt.error("Untrusted package redirect")
            } else {
                attempt.follow()
            }
        }))
        .build()
        .map_err(|_| "Nie można przygotować bezpiecznego pobierania.")?;
    download_from(&client, url, spec, output, check, progress).await
}

// Kept separate so tests can use an in-process HTTP fixture, never a production URL override.
async fn download_from(
    client: &reqwest::Client,
    url: reqwest::Url,
    spec: &PackageSpec,
    output: &mut File,
    check: &impl Fn() -> bool,
    mut progress: impl FnMut(u64),
) -> Result<(), String> {
    cancelled(check)?;
    let mut response = client
        .get(url)
        .header("Accept-Encoding", "identity")
        .send()
        .await
        .map_err(|_| {
            "Nie udało się połączyć z serwerem pakietu. Sprawdź sieć i spróbuj ponownie."
        })?;
    if response.status() != reqwest::StatusCode::OK {
        return Err("Pakiet nie jest dostępny na serwerze. Niczego nie zmieniono.".into());
    }
    if response
        .content_length()
        .is_some_and(|n| n != spec.archive_bytes)
    {
        return Err("Rozmiar pakietu na serwerze jest nieprawidłowy.".into());
    }
    let mut bytes = 0_u64;
    let mut digest = Sha256::new();
    loop {
        cancelled(check)?;
        let chunk = response.chunk().await.map_err(|_| {
            if check() {
                CANCELLED
            } else {
                "Pobieranie zostało przerwane. Możesz spróbować ponownie."
            }
        })?;
        let Some(chunk) = chunk else {
            break;
        };
        bytes += chunk.len() as u64;
        if bytes > spec.archive_bytes {
            return Err("Pobierany pakiet przekroczył dozwolony rozmiar.".into());
        }
        output
            .write_all(&chunk)
            .map_err(|_| "Nie można zapisać pakietu. Sprawdź wolne miejsce.")?;
        digest.update(&chunk);
        progress(bytes);
    }
    if bytes != spec.archive_bytes || format!("{:x}", digest.finalize()) != spec.sha256 {
        return Err("Nie zgadza się SHA-256 pobranego pakietu. Nie zostanie uruchomiony.".into());
    }
    output
        .flush()
        .map_err(|_| "Nie można zakończyć zapisu pakietu.")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        io::{Read, Write},
        net::TcpListener,
    };

    #[test]
    fn streams_a_local_fixture_and_rejects_hash_mismatch() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = std::thread::spawn(move || {
            for _ in 0..2 {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0; 4096];
                let _ = stream.read(&mut request);
                stream
                    .write_all(
                        b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                    )
                    .unwrap();
            }
        });
        let mut spec = PackageSpec {
            id: "test".into(),
            name: "Test".into(),
            version: "1".into(),
            url: None,
            sha256: format!("{:x}", Sha256::digest(b"abc")),
            archive_bytes: 3,
            unpacked_bytes: 3,
            file_count: 1,
            required_files: vec!["python/python.exe".into()],
        };
        let client = reqwest::Client::builder()
            .no_proxy()
            .timeout(Duration::from_secs(5))
            .build()
            .unwrap();
        let url = reqwest::Url::parse(&format!("http://{address}/package")).unwrap();
        let mut output = tempfile::tempfile().unwrap();
        tauri::async_runtime::block_on(download_from(
            &client,
            url.clone(),
            &spec,
            &mut output,
            &|| false,
            |_| {},
        ))
        .unwrap();
        spec.sha256 = "0".repeat(64);
        assert!(tauri::async_runtime::block_on(download_from(
            &client,
            url,
            &spec,
            &mut output,
            &|| false,
            |_| {}
        ))
        .is_err());
        server.join().unwrap();
    }
}
