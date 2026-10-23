//! Own only our private PostgreSQL, Java and Python children; never use PATH.

use super::paths::process_path;
use super::timings::StartupTimer;
use super::Status;
use serde_json::{json, Value};
use std::{
    fs,
    net::TcpListener,
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    time::{Duration, Instant},
};

#[derive(Clone)]
pub struct Backend {
    pub port: u16,
    token: String,
    client: reqwest::Client,
}

impl Backend {
    pub async fn request(
        &self,
        method: &str,
        path: &str,
        body: Option<&Value>,
        limit: usize,
    ) -> Result<(u16, Vec<u8>), String> {
        let method =
            reqwest::Method::from_bytes(method.as_bytes()).map_err(|_| "Nieprawidłowa metoda.")?;
        let mut request = self
            .client
            .request(method, format!("http://127.0.0.1:{}{}", self.port, path))
            .header("X-ScreenSort-Session", &self.token);
        if let Some(body) = body {
            request = request.json(body);
        }
        let mut response = request
            .send()
            .await
            .map_err(|_| "Brak połączenia z lokalnym backendem.")?;
        let status = response.status().as_u16();
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|_| "Przerwany odczyt odpowiedzi.")?
        {
            if chunk.len() > limit.saturating_sub(bytes.len()) {
                return Err("Odpowiedź przekroczyła limit.".into());
            }
            bytes.extend_from_slice(&chunk);
        }
        Ok((status, bytes))
    }
}

pub struct Owner {
    children: Vec<(&'static str, Child)>,
    job: Job,
    pg_ctl: PathBuf,
    db: PathBuf,
    pub backend: Option<Backend>,
    python_port: u16,
    python_token: String,
    runtime: PathBuf,
    session: PathBuf,
}

impl Owner {
    /// The installed AI package, or why there is none. A missing package is not worth retrying.
    pub fn analysis_package(&self) -> Result<PathBuf, String> {
        let data = self.db.parent().ok_or("Brak katalogu danych aplikacji.")?;
        super::packages::runtime(data, &self.runtime)
    }

    /// Optional AI failure must never take down the catalog or its database.
    pub async fn start_analysis<F>(&mut self, mut cancelled: F) -> Result<(), String>
    where
        F: FnMut() -> bool,
    {
        let ai_runtime = self.analysis_package()?;
        // Each attempt starts from a clean slate, so retries never pile up Python processes.
        self.retire_python();
        let py_state = self.session.join("python.json");
        let _ = fs::remove_file(&py_state);
        let mut python = Command::new(ai_runtime.join("python/python.exe"));
        python
            .args([
                "-I",
                "-B",
                "-m",
                "screensort_analysis.launch",
                "--state-file",
            ])
            .arg(&py_state)
            .arg("--model-cache")
            .arg(ai_runtime.join("models"))
            .env("SCREENSORT_ANALYSIS_TOKEN", &self.python_token)
            .current_dir(
                self.session
                    .parent()
                    .and_then(Path::parent)
                    .unwrap_or(&self.session),
            );
        let pid = self.spawn("Python", python, &self.session.join("python.log"))?;
        self.python_port = published(&py_state, pid, self).await?;
        let client = http_client()?;
        let deadline = Instant::now() + Duration::from_secs(180);
        while Instant::now() < deadline && !cancelled() {
            if let Some(name) = self.stopped_child() {
                return Err(format!(
                    "Proces {name} zakończył przygotowanie z błędem. Katalog pozostaje dostępny."
                ));
            }
            if let Ok(response) = client
                .get(format!("http://127.0.0.1:{}/ready", self.python_port))
                .bearer_auth(&self.python_token)
                .send()
                .await
            {
                if response.status().is_success() {
                    let backend = self.backend.as_ref().ok_or("Katalog jest niedostępny.")?;
                    let (status, _) = backend.request("POST", "/_internal/analysis-connection", Some(&json!({
                        "url": format!("http://127.0.0.1:{}", self.python_port), "token": self.python_token
                    })), 4096).await?;
                    return if status == 204 {
                        Ok(())
                    } else {
                        Err("Nie udało się połączyć silnika analizy z katalogiem.".into())
                    };
                }
            }
            pause(Duration::from_millis(250)).await;
        }
        Err("Przerwano przygotowanie AI lub przekroczono czas oczekiwania. Katalog pozostaje dostępny.".into())
    }

    fn spawn(
        &mut self,
        name: &'static str,
        mut command: Command,
        log: &Path,
    ) -> Result<u32, String> {
        let log = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(log)
            .map_err(|_| "Nie można otworzyć logu aplikacji.")?;
        command
            .stdin(Stdio::null())
            .stdout(log.try_clone().map_err(|_| "Błąd logu.")?)
            .stderr(log);
        hide(&mut command);
        let mut child = command.spawn().map_err(|_| {
            format!("Nie można uruchomić procesu {name}. Sprawdź kompletność instalacji.")
        })?;
        if let Err(error) = self.job.add(&child) {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error);
        }
        let id = child.id();
        self.children.push((name, child));
        Ok(id)
    }

    /// Stops and forgets the Python child, if any. The catalog keeps running.
    fn retire_python(&mut self) {
        self.children.retain_mut(|(name, child)| {
            if *name != "Python" {
                return true;
            }
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
            false
        });
        self.python_port = 0;
    }

    pub fn stopped_child(&mut self) -> Option<&'static str> {
        self.children
            .iter_mut()
            .find_map(|(name, child)| child.try_wait().ok().flatten().map(|_| *name))
    }

    pub async fn stop(&mut self) {
        if let Some(backend) = &self.backend {
            let _ = backend
                .request("POST", "/_internal/shutdown", None, 4096)
                .await;
        }
        if self.python_port != 0 {
            if let Ok(client) = http_client() {
                let _ = client
                    .post(format!(
                        "http://127.0.0.1:{}/_internal/shutdown",
                        self.python_port
                    ))
                    .bearer_auth(&self.python_token)
                    .send()
                    .await;
            }
        }
        pause(Duration::from_millis(500)).await;
        for (name, child) in &mut self.children {
            if *name != "PostgreSQL" && child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
                let _ = child.wait();
            }
        }
        let mut command = Command::new(&self.pg_ctl);
        command
            .args(["-D"])
            .arg(&self.db)
            .args(["stop", "-m", "fast", "-w", "-t", "8"]);
        hide(&mut command);
        let _ = tauri::async_runtime::spawn_blocking(move || {
            command.stdout(Stdio::null()).stderr(Stdio::null()).status()
        })
        .await;
        for (_, child) in &mut self.children {
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
        }
    }
}

pub fn random_secret() -> Result<String, String> {
    let mut bytes = [0_u8; 32];
    getrandom::fill(&mut bytes).map_err(|_| "Brak bezpiecznego źródła losowości.")?;
    Ok(bytes.iter().map(|byte| format!("{byte:02x}")).collect())
}

pub fn http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(2))
        .timeout(Duration::from_secs(12))
        .build()
        .map_err(|_| "Nie można przygotować komunikacji lokalnej.".into())
}

pub async fn pause(duration: Duration) {
    let _ = tauri::async_runtime::spawn_blocking(move || std::thread::sleep(duration)).await;
}

fn run(mut command: Command, label: &str, log: &Path) -> Result<Vec<u8>, String> {
    let errors = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(log)
        .map_err(|_| "Nie można otworzyć logu narzędzia bazy.")?;
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(errors)
        .env("PGCONNECT_TIMEOUT", "5");
    hide(&mut command);
    let mut child = command
        .spawn()
        .map_err(|_| format!("Nie można uruchomić {label}."))?;
    let job = Job::new()?;
    if let Err(error) = job.add(&child) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }
    let deadline = Instant::now() + Duration::from_secs(60);
    while child
        .try_wait()
        .map_err(|_| "Błąd oczekiwania na narzędzie bazy.")?
        .is_none()
    {
        if Instant::now() >= deadline {
            let _ = child.kill();
            let _ = child.wait();
            return Err(format!(
                "{label}: przekroczono czas oczekiwania. Dane pozostawiono bez zmian."
            ));
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    let output = child
        .wait_with_output()
        .map_err(|_| "Błąd odczytu narzędzia bazy.")?;
    if !output.status.success() {
        return Err(format!(
            "{label} zakończył się błędem. Nie usuwaj istniejącej bazy; sprawdź logi instalacji."
        ));
    }
    Ok(output.stdout)
}

fn available_port() -> Result<u16, String> {
    TcpListener::bind(("127.0.0.1", 0))
        .and_then(|socket| socket.local_addr().map(|a| a.port()))
        .map_err(|_| "Brak wolnego portu lokalnego.".into())
}

async fn published(path: &Path, pid: u32, owner: &mut Owner) -> Result<u16, String> {
    let deadline = Instant::now() + Duration::from_secs(120);
    while Instant::now() < deadline {
        if let Some(name) = owner.stopped_child() {
            return Err(format!(
                "Proces {name} przerwał start. Sprawdź lokalne logi aplikacji."
            ));
        }
        if let Ok(bytes) = fs::read(path) {
            if bytes.len() < 4096 {
                if let Ok(value) = serde_json::from_slice::<Value>(&bytes) {
                    if value["pid"].as_u64() == Some(pid as u64) {
                        if let Some(port) = value["port"]
                            .as_u64()
                            .filter(|port| *port > 0 && *port <= 65535)
                        {
                            return Ok(port as u16);
                        }
                    }
                }
            }
        }
        pause(Duration::from_millis(150)).await;
    }
    Err("Przekroczono czas uruchamiania lokalnego backendu.".into())
}

/// Starts PostgreSQL and the backend, and records how long each stage took (C1: measure first).
pub async fn start<F>(runtime: &Path, data: &Path, update: F) -> Result<Owner, String>
where
    F: FnMut(Status, Option<Backend>),
{
    let mut timer = StartupTimer::start();
    let result = start_stages(runtime, data, update, &mut timer).await;
    timer.save(data, if result.is_ok() { "ok" } else { "failed" });
    result
}

async fn start_stages<F>(
    runtime: &Path,
    data: &Path,
    mut update: F,
    timer: &mut StartupTimer,
) -> Result<Owner, String>
where
    F: FnMut(Status, Option<Backend>),
{
    // Tauri can return \\?\ paths. initdb's internal shell cannot execute //?/ paths.
    // Normalize before deriving *any* executable, model, password or data path.
    let runtime_path = process_path(runtime)?;
    let data_path = process_path(data)?;
    let runtime = runtime_path.as_path();
    let data = data_path.as_path();
    for executable in [
        "java/bin/java.exe",
        "postgres/bin/initdb.exe",
        "postgres/bin/postgres.exe",
        "backend.jar",
    ] {
        if !runtime.join(executable).is_file() {
            return Err("Instalacja jest niekompletna: brakuje dołączonego runtime. Zbuduj pełny instalator ScreenSort.".into());
        }
    }
    fs::create_dir_all(data).map_err(|_| "Nie można utworzyć katalogu danych aplikacji.")?;
    let session = data.join("sessions").join(&random_secret()?[..24]);
    fs::create_dir_all(&session).map_err(|_| "Nie można utworzyć sesji aplikacji.")?;
    let password_file = data.join("database-password");
    let db = data.join("postgres18");
    let password = if password_file.exists() {
        let value = fs::read_to_string(&password_file)
            .map_err(|_| "Nie można odczytać konfiguracji bazy.")?;
        if value.trim().len() != 64 || !value.trim().bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err("Nieprawidłowa konfiguracja istniejącej bazy. Nie usuwaj danych.".into());
        }
        value.trim().to_string()
    } else {
        if db.exists() {
            return Err(
                "Istnieją dane bazy bez konfiguracji dostępu. Nie inicjalizujemy ich ponownie."
                    .into(),
            );
        }
        let value = random_secret()?;
        fs::write(&password_file, format!("{value}\n"))
            .map_err(|_| "Nie można zapisać konfiguracji bazy.")?;
        value
    };
    let python_token = random_secret()?;
    let desktop_token = random_secret()?;
    let mut owner = Owner {
        children: Vec::new(),
        job: Job::new()?,
        pg_ctl: runtime.join("postgres/bin/pg_ctl.exe"),
        db: db.clone(),
        backend: None,
        python_port: 0,
        python_token: python_token.clone(),
        runtime: runtime.to_path_buf(),
        session: session.clone(),
    };
    update(
        Status {
            phase: "Przygotowujemy prywatną bazę PostgreSQL…".into(),
            ..Status::default()
        },
        None,
    );
    timer.mark("prepare");
    if !db.join("PG_VERSION").exists() {
        if db.exists() {
            return Err(
                "Przerwana inicjalizacja bazy. Nie nadpisujemy istniejącego katalogu danych."
                    .into(),
            );
        }
        let mut init = Command::new(runtime.join("postgres/bin/initdb.exe"));
        init.arg("-D")
            .arg(&db)
            .args([
                "-U",
                "screensort",
                "--auth=scram-sha-256",
                "--encoding=UTF8",
                "--locale=C",
                "--pwfile",
            ])
            .arg(&password_file);
        let result = run(
            init,
            "Inicjalizacja bazy",
            &session.join("database-tools.log"),
        )?;
        let _ = fs::write(session.join("initdb.log"), result);
        timer.mark("initdb");
    } else if fs::read_to_string(db.join("PG_VERSION"))
        .map_err(|_| "Nie można odczytać wersji bazy.")?
        .trim()
        != "18"
    {
        return Err("Wersja istniejącej bazy wymaga migracji. Dane pozostawiono bez zmian.".into());
    }
    let pg_port = available_port()?;
    let mut pg = Command::new(runtime.join("postgres/bin/postgres.exe"));
    pg.arg("-D")
        .arg(&db)
        .args(["-h", "127.0.0.1", "-p"])
        .arg(pg_port.to_string())
        .args(["-c", "shared_buffers=32MB", "-c", "max_connections=16"]);
    owner.spawn("PostgreSQL", pg, &session.join("postgres.log"))?;
    let mut pg_ready = false;
    for _ in 0..100 {
        if owner.stopped_child().is_some() {
            return Err("Lokalna baza nie mogła się uruchomić. Sprawdź logi sesji.".into());
        }
        // An open TCP port is not PostgreSQL readiness: recovery can still be running.
        let mut probe = Command::new(runtime.join("postgres/bin/pg_isready.exe"));
        probe
            .args(["-h", "127.0.0.1", "-p"])
            .arg(pg_port.to_string())
            .args(["-U", "screensort", "-d", "postgres", "-t", "1"])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        hide(&mut probe);
        if probe.status().is_ok_and(|status| status.success()) {
            pg_ready = true;
            break;
        }
        pause(Duration::from_millis(100)).await;
    }
    if !pg_ready {
        return Err("Baza nie odpowiedziała w wymaganym czasie.".into());
    }
    timer.mark("postgres");
    let mut check = Command::new(runtime.join("postgres/bin/psql.exe"));
    check
        .args(["-h", "127.0.0.1", "-p"])
        .arg(pg_port.to_string())
        .args([
            "-U",
            "screensort",
            "-d",
            "postgres",
            "-Atc",
            "SELECT 1 FROM pg_database WHERE datname='screensort'",
        ])
        .env("PGPASSWORD", &password);
    if String::from_utf8_lossy(&run(
        check,
        "Sprawdzenie bazy",
        &session.join("database-tools.log"),
    )?)
    .trim()
        != "1"
    {
        let mut create = Command::new(runtime.join("postgres/bin/createdb.exe"));
        create
            .args(["-h", "127.0.0.1", "-p"])
            .arg(pg_port.to_string())
            .args(["-U", "screensort", "screensort"])
            .env("PGPASSWORD", &password);
        run(
            create,
            "Utworzenie katalogu bazy",
            &session.join("database-tools.log"),
        )?;
    }
    timer.mark("database");
    let java_state = session.join("java.json");
    update(
        Status {
            phase: "Uruchamiamy backend i sprawdzamy migracje…".into(),
            ..Status::default()
        },
        None,
    );
    let mut java = Command::new(runtime.join("java/bin/java.exe"));
    java.args(["-Xmx512m", "-jar"])
        .arg(runtime.join("backend.jar"))
        .arg("--spring.profiles.active=desktop")
        .env(
            "SCREENSORT_DB_URL",
            format!("jdbc:postgresql://127.0.0.1:{pg_port}/screensort"),
        )
        .env("SCREENSORT_DB_PASSWORD", &password)
        .env("SCREENSORT_DESKTOP_TOKEN", &desktop_token)
        .env("SCREENSORT_DESKTOP_STATE_FILE", &java_state)
        .env_remove("SCREENSORT_ANALYSIS_TOKEN")
        .env_remove("SCREENSORT_ANALYSIS_URL")
        .env_remove("JAVA_TOOL_OPTIONS")
        .env_remove("JDK_JAVA_OPTIONS")
        .env_remove("_JAVA_OPTIONS")
        .current_dir(data);
    let java_pid = owner.spawn("Java", java, &session.join("java.log"))?;
    let port = published(&java_state, java_pid, &mut owner).await?;
    // JVM start, Spring context and Flyway migrations; java.log in the session has the split.
    timer.mark("backend");
    let backend = Backend {
        port,
        token: desktop_token,
        client: http_client()?,
    };
    if backend
        .request("GET", "/_internal/ready", None, 4096)
        .await?
        .0
        != 200
    {
        return Err("Nie potwierdzono tożsamości backendu.".into());
    }
    timer.mark("ready");
    owner.backend = Some(backend.clone());
    update(
        Status {
            phase: "Katalog gotowy. AI uruchomisz, gdy będzie potrzebne.".into(),
            backend_ready: true,
            analysis_ready: false,
            analysis_starting: false,
            error: None,
        },
        Some(backend.clone()),
    );
    Ok(owner)
}

fn hide(command: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
}

#[cfg(windows)]
struct Job(windows_sys::Win32::Foundation::HANDLE);
#[cfg(windows)]
unsafe impl Send for Job {}

#[cfg(windows)]
impl Job {
    fn new() -> Result<Self, String> {
        use windows_sys::Win32::{Foundation::CloseHandle, System::JobObjects::*};
        unsafe {
            let handle = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if handle.is_null() {
                return Err("Nie można utworzyć nadzoru procesów.".into());
            }
            let mut info: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                &info as *const _ as *const std::ffi::c_void,
                std::mem::size_of_val(&info) as u32,
            ) == 0
            {
                CloseHandle(handle);
                return Err("Nie można skonfigurować nadzoru procesów.".into());
            }
            Ok(Self(handle))
        }
    }
    fn add(&self, child: &Child) -> Result<(), String> {
        use std::os::windows::io::AsRawHandle;
        if unsafe {
            windows_sys::Win32::System::JobObjects::AssignProcessToJobObject(
                self.0,
                child.as_raw_handle(),
            )
        } == 0
        {
            return Err("Nie można objąć procesu bezpiecznym nadzorem.".into());
        }
        Ok(())
    }
}
#[cfg(windows)]
impl Drop for Job {
    fn drop(&mut self) {
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

#[cfg(not(windows))]
struct Job;
#[cfg(not(windows))]
impl Job {
    fn new() -> Result<Self, String> {
        Err("Ten pakiet runtime jest przeznaczony dla Windows x64.".into())
    }
    fn add(&self, _: &Child) -> Result<(), String> {
        Ok(())
    }
}
