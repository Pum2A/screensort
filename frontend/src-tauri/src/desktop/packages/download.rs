use super::{
    archive::cancelled,
    catalog::{download_url, PackageSpec},
};
use sha2::{Digest, Sha256};
use std::{
    fs::File,
    io::{Read, Seek, SeekFrom, Write},
    time::Duration,
};

/// Connection drops are retried this many times, each continuing where the file ended.
const ATTEMPTS: u32 = 6;
const FIRST_RETRY_PAUSE: Duration = Duration::from_secs(1);
const INTERRUPTED: &str = "Pobieranie zostało przerwane. Możesz spróbować ponownie.";

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

/// Downloads into `output`, continuing after the bytes it already holds (a previous, interrupted
/// download of the same package). The whole file is checked against the pinned SHA-256 at the end.
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
    download_from(
        &client,
        url,
        spec,
        output,
        check,
        progress,
        FIRST_RETRY_PAUSE,
    )
    .await
}

/// Pause before retry number `retry` (1-based): 1 s, 2 s, 4 s, 8 s, 16 s.
fn retry_pause(first: Duration, retry: u32) -> Duration {
    first * 2_u32.pow(retry.saturating_sub(1).min(4))
}

/// Sleeps in small steps so a cancel is noticed quickly. Runs on the install worker thread.
fn wait(duration: Duration, check: &impl Fn() -> bool) -> Result<(), String> {
    let step = Duration::from_millis(100);
    let mut left = duration;
    while !left.is_zero() {
        cancelled(check)?;
        let now = left.min(step);
        std::thread::sleep(now);
        left -= now;
    }
    cancelled(check)
}

/// Re-reads what the file already holds, so the final hash covers the whole package.
fn existing(output: &mut File, spec: &PackageSpec) -> Result<(u64, Sha256), String> {
    let mut digest = Sha256::new();
    let length = output
        .metadata()
        .map_err(|_| "Nie można sprawdzić pobranej części pakietu.")?
        .len();
    if length > spec.archive_bytes {
        restart(output)?;
        return Ok((0, digest));
    }
    output
        .seek(SeekFrom::Start(0))
        .map_err(|_| "Nie można odczytać pobranej części pakietu.")?;
    let mut buffer = vec![0_u8; 64 * 1024];
    let mut read = 0_u64;
    while read < length {
        let count = output
            .read(&mut buffer)
            .map_err(|_| "Nie można odczytać pobranej części pakietu.")?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
        read += count as u64;
    }
    output
        .seek(SeekFrom::Start(read))
        .map_err(|_| "Nie można wznowić zapisu pakietu.")?;
    Ok((read, digest))
}

fn restart(output: &mut File) -> Result<(), String> {
    output
        .set_len(0)
        .and_then(|_| output.seek(SeekFrom::Start(0)).map(|_| ()))
        .map_err(|_| "Nie można rozpocząć pobierania od nowa.".into())
}

/// `bytes 1000-4999/5000` must continue exactly at `start` and describe the whole package.
fn continues_at(content_range: Option<&str>, start: u64, total: u64) -> bool {
    let Some(range) = content_range.and_then(|value| value.strip_prefix("bytes ")) else {
        return false;
    };
    let Some((span, size)) = range.split_once('/') else {
        return false;
    };
    let Some((first, last)) = span.split_once('-') else {
        return false;
    };
    first.parse::<u64>().ok() == Some(start)
        && last.parse::<u64>().ok() == Some(total.saturating_sub(1))
        && size.parse::<u64>().ok() == Some(total)
}

// Kept separate so tests can use an in-process HTTP fixture, never a production URL override.
async fn download_from(
    client: &reqwest::Client,
    url: reqwest::Url,
    spec: &PackageSpec,
    output: &mut File,
    check: &impl Fn() -> bool,
    mut progress: impl FnMut(u64),
    first_pause: Duration,
) -> Result<(), String> {
    cancelled(check)?;
    let (mut bytes, mut digest) = existing(output, spec)?;
    progress(bytes);
    let mut retry = 0;
    while bytes < spec.archive_bytes {
        let mut request = client
            .get(url.clone())
            .header("Accept-Encoding", "identity");
        if bytes > 0 {
            request = request.header("Range", format!("bytes={bytes}-"));
        }
        let outcome = match request.send().await {
            Err(_) => {
                Err("Nie udało się połączyć z serwerem pakietu. Sprawdź sieć i spróbuj ponownie.")
            }
            Ok(mut response) => {
                let status = response.status();
                let resumed = status == reqwest::StatusCode::PARTIAL_CONTENT
                    && continues_at(
                        response
                            .headers()
                            .get("Content-Range")
                            .and_then(|value| value.to_str().ok()),
                        bytes,
                        spec.archive_bytes,
                    );
                if status == reqwest::StatusCode::OK {
                    // The server ignored the range: take the whole package from the start.
                    if bytes > 0 {
                        restart(output)?;
                        bytes = 0;
                        digest = Sha256::new();
                        progress(0);
                    }
                    if response
                        .content_length()
                        .is_some_and(|n| n != spec.archive_bytes)
                    {
                        return Err("Rozmiar pakietu na serwerze jest nieprawidłowy.".into());
                    }
                } else if !resumed {
                    return Err(
                        "Pakiet nie jest dostępny na serwerze. Niczego nie zmieniono.".into(),
                    );
                }
                let mut streamed = Ok(());
                loop {
                    cancelled(check)?;
                    let chunk = match response.chunk().await {
                        Ok(Some(chunk)) => chunk,
                        Ok(None) => break,
                        Err(_) => {
                            cancelled(check)?;
                            streamed = Err(INTERRUPTED);
                            break;
                        }
                    };
                    if bytes + chunk.len() as u64 > spec.archive_bytes {
                        return Err("Pobierany pakiet przekroczył dozwolony rozmiar.".into());
                    }
                    output
                        .write_all(&chunk)
                        .map_err(|_| "Nie można zapisać pakietu. Sprawdź wolne miejsce.")?;
                    digest.update(&chunk);
                    bytes += chunk.len() as u64;
                    progress(bytes);
                }
                if streamed.is_ok() && bytes < spec.archive_bytes {
                    streamed = Err(INTERRUPTED);
                }
                streamed
            }
        };
        if let Err(message) = outcome {
            retry += 1;
            if retry >= ATTEMPTS {
                // The bytes stay on disk, so the next "install" continues from here.
                output
                    .flush()
                    .map_err(|_| "Nie można zapisać pakietu. Sprawdź wolne miejsce.")?;
                return Err(message.into());
            }
            wait(retry_pause(first_pause, retry), check)?;
        }
    }
    if format!("{:x}", digest.finalize()) != spec.sha256 {
        // A wrong file must not be resumed: the next attempt starts from zero.
        restart(output)?;
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
        sync::{Arc, Mutex},
    };

    fn spec(content: &[u8]) -> PackageSpec {
        PackageSpec {
            id: "test".into(),
            name: "Test".into(),
            version: "1".into(),
            url: None,
            sha256: format!("{:x}", Sha256::digest(content)),
            archive_bytes: content.len() as u64,
            unpacked_bytes: content.len() as u64,
            file_count: 1,
            required_files: vec!["python/python.exe".into()],
        }
    }

    /// Serves the given raw responses in order and records each request's Range header.
    fn serve(responses: Vec<&'static [u8]>) -> (reqwest::Url, Arc<Mutex<Vec<String>>>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let ranges = Arc::new(Mutex::new(Vec::new()));
        let seen = Arc::clone(&ranges);
        std::thread::spawn(move || {
            for response in responses {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0; 4096];
                let length = stream.read(&mut request).unwrap_or(0);
                let text = String::from_utf8_lossy(&request[..length]).to_lowercase();
                let range = text
                    .lines()
                    .find_map(|line| line.strip_prefix("range: "))
                    .unwrap_or("")
                    .to_string();
                seen.lock().unwrap().push(range);
                let _ = stream.write_all(response);
            }
        });
        (
            reqwest::Url::parse(&format!("http://{address}/package")).unwrap(),
            ranges,
        )
    }

    fn client() -> reqwest::Client {
        reqwest::Client::builder()
            .no_proxy()
            .timeout(Duration::from_secs(5))
            .build()
            .unwrap()
    }

    fn run(url: reqwest::Url, spec: &PackageSpec, output: &mut File) -> Result<(), String> {
        tauri::async_runtime::block_on(download_from(
            &client(),
            url,
            spec,
            output,
            &|| false,
            |_| {},
            Duration::ZERO,
        ))
    }

    fn contents(output: &mut File) -> Vec<u8> {
        let mut bytes = Vec::new();
        output.seek(SeekFrom::Start(0)).unwrap();
        output.read_to_end(&mut bytes).unwrap();
        bytes
    }

    #[test]
    fn streams_a_local_fixture_and_rejects_hash_mismatch() {
        let (url, _) = serve(vec![
            b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
            b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
        ]);
        let mut spec = spec(b"abc");
        let mut output = tempfile::tempfile().unwrap();
        run(url.clone(), &spec, &mut output).unwrap();
        assert_eq!(contents(&mut output), b"abc");
        spec.sha256 = "0".repeat(64);
        let mut fresh = tempfile::tempfile().unwrap();
        assert!(run(url, &spec, &mut fresh).is_err());
        assert!(contents(&mut fresh).is_empty());
    }

    #[test]
    fn a_dropped_connection_continues_with_a_range_request() {
        let (url, ranges) = serve(vec![
            b"HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nabc",
            b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 3-5/6\r\nContent-Length: 3\r\nConnection: close\r\n\r\ndef",
        ]);
        let mut output = tempfile::tempfile().unwrap();
        run(url, &spec(b"abcdef"), &mut output).unwrap();
        assert_eq!(contents(&mut output), b"abcdef");
        assert_eq!(*ranges.lock().unwrap(), ["", "bytes=3-"]);
    }

    #[test]
    fn a_part_left_by_an_earlier_run_is_continued() {
        let (url, ranges) = serve(vec![
            b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 4-5/6\r\nContent-Length: 2\r\nConnection: close\r\n\r\nef",
        ]);
        let mut output = tempfile::tempfile().unwrap();
        output.write_all(b"abcd").unwrap();
        run(url, &spec(b"abcdef"), &mut output).unwrap();
        assert_eq!(contents(&mut output), b"abcdef");
        assert_eq!(*ranges.lock().unwrap(), ["bytes=4-"]);
    }

    #[test]
    fn a_server_without_ranges_starts_again_from_zero() {
        let (url, _) = serve(vec![
            b"HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nabcdef",
        ]);
        let mut output = tempfile::tempfile().unwrap();
        output.write_all(b"xyz").unwrap();
        run(url, &spec(b"abcdef"), &mut output).unwrap();
        assert_eq!(contents(&mut output), b"abcdef");
    }

    #[test]
    fn content_range_must_continue_exactly_where_the_file_ends() {
        assert!(continues_at(Some("bytes 3-5/6"), 3, 6));
        assert!(!continues_at(Some("bytes 0-5/6"), 3, 6));
        assert!(!continues_at(Some("bytes 3-5/7"), 3, 6));
        assert!(!continues_at(Some("bytes 3-4/6"), 3, 6));
        assert!(!continues_at(None, 3, 6));
    }

    #[test]
    fn retry_pauses_grow_and_stop_growing() {
        let first = Duration::from_secs(1);
        let pauses: Vec<u64> = (1..=6)
            .map(|retry| retry_pause(first, retry).as_secs())
            .collect();
        assert_eq!(pauses, [1, 2, 4, 8, 16, 16]);
    }
}
