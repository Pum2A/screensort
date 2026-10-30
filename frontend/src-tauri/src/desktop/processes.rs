//! Own only our private PostgreSQL, Java, Python and llama-server children; never use PATH.

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
    llama: Option<Llama>,
    llama_token: String,
    runtime: PathBuf,
    session: PathBuf,
    java: Option<JavaLaunch>,
    java_restarts: u32,
}

/// Everything needed to start the backend again after a crash, without a new database session.
struct JavaLaunch {
    data: PathBuf,
    pg_port: u16,
    password: String,
    token: String,
}

/// A running llama-server and what the backend needs to reach it again after a restart.
struct Llama {
    port: u16,
    model: String,
    device: &'static str,
}

/// How often a crashed backend is started again in one app run before the user is asked to restart.
pub const MAX_JAVA_RESTARTS: u32 = 3;
const CLOSING: &str = "Aplikacja kończy pracę.";

fn java_command(runtime: &Path, launch: &JavaLaunch, state: &Path) -> Command {
    let mut java = Command::new(runtime.join("java/bin/java.exe"));
    java.args(["-Xmx512m", "-jar"])
        .arg(runtime.join("backend.jar"))
        .arg("--spring.profiles.active=desktop")
        .env(
            "SCREENSORT_DB_URL",
            format!("jdbc:postgresql://127.0.0.1:{}/screensort", launch.pg_port),
        )
        .env("SCREENSORT_DB_PASSWORD", &launch.password)
        .env("SCREENSORT_DESKTOP_TOKEN", &launch.token)
        .env("SCREENSORT_DESKTOP_STATE_FILE", state)
        .env_remove("SCREENSORT_ANALYSIS_TOKEN")
        .env_remove("SCREENSORT_ANALYSIS_URL")
        .env_remove("JAVA_TOOL_OPTIONS")
        .env_remove("JDK_JAVA_OPTIONS")
        .env_remove("_JAVA_OPTIONS")
        .current_dir(&launch.data);
    java
}

impl Owner {
    /// Fails when no AI package is installed, with the reason. A missing package is not worth retrying.
    pub fn analysis_package(&self) -> Result<(), String> {
        let data = self.db.parent().ok_or("Brak katalogu danych aplikacji.")?;
        if super::packages::qwen(data).is_some() {
            return Ok(());
        }
        super::packages::runtime(data, &self.runtime).map(|_| ())
    }

    /// Optional AI failure must never take down the catalog or its database.
    pub async fn start_analysis<F>(&mut self, cancelled: F) -> Result<(), String>
    where
        F: FnMut() -> bool + Send,
    {
        // Measured like the catalog start, so the log shows how long the AI models take to load.
        // Every retry is its own measured start.
        let mut timer = StartupTimer::start("ai");
        let result = self.start_analysis_stages(cancelled, &mut timer).await;
        if let Some(data) = self.db.parent() {
            timer.save(data, if result.is_ok() { "ok" } else { "failed" });
        }
        result
    }

    /// Qwen (when installed) names and sorts the screenshots; Python (when installed) reads their
    /// text. With Qwen running, a Python failure leaves analysis usable, only without OCR.
    async fn start_analysis_stages<F>(
        &mut self,
        mut cancelled: F,
        timer: &mut StartupTimer,
    ) -> Result<(), String>
    where
        F: FnMut() -> bool + Send,
    {
        let data = self
            .db
            .parent()
            .ok_or("Brak katalogu danych aplikacji.")?
            .to_path_buf();
        let python = super::packages::runtime(&data, &self.runtime);
        if let Some((directory, spec)) = super::packages::qwen(&data) {
            self.start_llama(&directory, &spec, &mut cancelled).await?;
            timer.mark("qwen");
            if let Ok(ai_runtime) = python {
                if self
                    .start_python(&ai_runtime, &mut cancelled, timer)
                    .await
                    .is_err()
                {
                    eprintln!("Python OCR did not start; Qwen keeps working without OCR");
                    self.retire_engine("Python");
                }
            }
            return Ok(());
        }
        self.start_python(&python?, &mut cancelled, timer).await
    }

    /// Tries the GPU first when one is listed. A model that does not fit in video memory makes
    /// llama-server exit while loading, so one more try runs it on the processor.
    async fn start_llama<F>(
        &mut self,
        directory: &Path,
        spec: &super::packages::PackageSpec,
        cancelled: &mut F,
    ) -> Result<(), String>
    where
        F: FnMut() -> bool + Send,
    {
        let server = directory.join("llama-server.exe");
        let devices: &[&'static str] = if gpu_available(&server, &self.session).await {
            &["gpu", "cpu"]
        } else {
            &["cpu"]
        };
        let mut result = Err(String::new());
        for device in devices {
            result = match self.launch_llama(directory, spec, device, cancelled).await {
                Ok(()) => return Ok(()),
                Err((message, crashed)) => {
                    self.retire_engine("Llama");
                    if !crashed {
                        return Err(message);
                    }
                    Err(message)
                }
            };
        }
        result
    }

    /// The error says whether llama-server itself stopped, which is worth one try on the CPU.
    async fn launch_llama<F>(
        &mut self,
        directory: &Path,
        spec: &super::packages::PackageSpec,
        device: &'static str,
        cancelled: &mut F,
    ) -> Result<(), (String, bool)>
    where
        F: FnMut() -> bool + Send,
    {
        self.retire_engine("Llama");
        let port = available_port().map_err(|message| (message, false))?;
        let mut command = Command::new(directory.join("llama-server.exe"));
        command
            .arg("-m")
            .arg(directory.join("models/qwen.gguf"))
            .arg("--mmproj")
            .arg(directory.join("models/mmproj.gguf"))
            .args(["-ngl", if device == "gpu" { "99" } else { "0" }])
            .args(["--image-max-tokens", "256", "-np", "1", "-c", "4096"])
            .args([
                "--host",
                "127.0.0.1",
                "--port",
                &port.to_string(),
                "--no-webui",
            ])
            // In the environment, not on the command line, so other processes cannot read it.
            .env("LLAMA_API_KEY", &self.llama_token)
            .current_dir(directory);
        self.spawn("Llama", command, &self.session.join("llama.log"))
            .map_err(|message| (message, false))?;
        let client = http_client().map_err(|message| (message, false))?;
        let deadline = Instant::now() + Duration::from_secs(180);
        while Instant::now() < deadline {
            if cancelled() {
                return Err((CLOSING.into(), false));
            }
            if let Some(name) = self.stopped_child() {
                return Err((
                    format!("Proces {name} zakończył przygotowanie z błędem. Katalog pozostaje dostępny."),
                    name == "Llama",
                ));
            }
            // /health answers 503 while the model loads and 200 once it can take requests.
            if let Ok(response) = client
                .get(format!("http://127.0.0.1:{port}/health"))
                .send()
                .await
            {
                if response.status().is_success() {
                    let llama = Llama {
                        port,
                        model: spec.model.clone().unwrap_or_default(),
                        device,
                    };
                    if !connect_llama(self.backend.clone(), &self.llama_token, &llama).await {
                        return Err((
                            "Nie udało się połączyć silnika Qwen z katalogiem.".into(),
                            false,
                        ));
                    }
                    self.llama = Some(llama);
                    return Ok(());
                }
            }
            pause(Duration::from_millis(250)).await;
        }
        Err((
            "Przekroczono czas ładowania modelu Qwen. Katalog pozostaje dostępny.".into(),
            false,
        ))
    }

    async fn start_python<F>(
        &mut self,
        ai_runtime: &Path,
        mut cancelled: F,
        timer: &mut StartupTimer,
    ) -> Result<(), String>
    where
        F: FnMut() -> bool + Send,
    {
        // Each attempt starts from a clean slate, so retries never pile up Python processes.
        self.retire_engine("Python");
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
        self.python_port = published(&py_state, pid, self, &mut cancelled).await?;
        timer.mark("spawn");
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
                    // Model loading and the warm-up analysis end when /ready answers.
                    timer.mark("models");
                    let backend = self.backend.as_ref().ok_or("Katalog jest niedostępny.")?;
                    let (status, _) = backend.request("POST", "/_internal/analysis-connection", Some(&json!({
                        "url": format!("http://127.0.0.1:{}", self.python_port), "token": self.python_token
                    })), 4096).await?;
                    timer.mark("connect");
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

    async fn start_backend(
        &mut self,
        state: &Path,
        cancelled: &mut (dyn FnMut() -> bool + Send),
    ) -> Result<Backend, String> {
        let launch = self.java.as_ref().ok_or("Brak konfiguracji backendu.")?;
        let java = java_command(&self.runtime, launch, state);
        let token = launch.token.clone();
        let pid = self.spawn("Java", java, &self.session.join("java.log"))?;
        let port = published(state, pid, self, cancelled).await?;
        let backend = Backend {
            port,
            token,
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
        self.backend = Some(backend.clone());
        Ok(backend)
    }

    /// Starts the backend again after it stopped, on the same database. Returns the new backend
    /// and whether the running AI engine could be connected to it again.
    pub async fn restart_java(
        &mut self,
        cancelled: &mut (dyn FnMut() -> bool + Send),
    ) -> Result<(Backend, bool), String> {
        if self.java_restarts >= MAX_JAVA_RESTARTS {
            return Err("Backend zatrzymał się kilka razy. Uruchom ScreenSort ponownie; zapisane dane pozostają w bazie.".into());
        }
        self.java_restarts += 1;
        self.backend = None;
        self.children.retain_mut(|(name, child)| {
            if *name != "Java" {
                return true;
            }
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
            false
        });
        let state = self
            .session
            .join(format!("java-restart-{}.json", self.java_restarts));
        let backend = self.start_backend(&state, cancelled).await?;
        let mut analysis = false;
        if self.python_port != 0 {
            let connection = json!({
                "url": format!("http://127.0.0.1:{}", self.python_port), "token": self.python_token
            });
            analysis = matches!(
                backend
                    .request(
                        "POST",
                        "/_internal/analysis-connection",
                        Some(&connection),
                        4096
                    )
                    .await,
                Ok((204, _))
            );
        }
        // Either engine is enough for analysis: Qwen alone, or Python alone as before.
        if let Some(llama) = &self.llama {
            analysis |= connect_llama(Some(backend.clone()), &self.llama_token, llama).await;
        }
        Ok((backend, analysis))
    }

    /// Stops and forgets an AI engine (Python or Llama) and where it listened. The catalog keeps running.
    pub fn retire_engine(&mut self, process: &str) {
        self.retire(process);
        match process {
            "Python" => self.python_port = 0,
            "Llama" => self.llama = None,
            _ => {}
        }
    }

    fn retire(&mut self, process: &str) {
        self.children.retain_mut(|(name, child)| {
            if *name != process {
                return true;
            }
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
            false
        });
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

/// Tells the backend where llama-server listens. Takes no `Owner`, so the future stays `Send`.
async fn connect_llama(backend: Option<Backend>, token: &str, llama: &Llama) -> bool {
    let Some(backend) = backend else {
        return false;
    };
    let connection = json!({
        "url": format!("http://127.0.0.1:{}", llama.port),
        "token": token,
        "model": llama.model,
        "device": llama.device,
    });
    matches!(
        backend
            .request(
                "POST",
                "/_internal/vision-connection",
                Some(&connection),
                4096
            )
            .await,
        Ok((204, _))
    )
}

/// Asks llama-server which devices it can use. Any Vulkan device means the model runs on the GPU;
/// no answer, an error or only a CPU means it runs on the processor.
async fn gpu_available(server: &Path, session: &Path) -> bool {
    let mut command = Command::new(server);
    command.arg("--list-devices");
    let log = session.join("llama.log");
    tauri::async_runtime::spawn_blocking(move || run(command, "llama-server", &log))
        .await
        .ok()
        .and_then(Result::ok)
        .is_some_and(|output| has_gpu(&String::from_utf8_lossy(&output)))
}

fn has_gpu(devices: &str) -> bool {
    devices
        .lines()
        .any(|line| line.trim_start().starts_with("Vulkan"))
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

async fn published(
    path: &Path,
    pid: u32,
    owner: &mut Owner,
    cancelled: &mut (dyn FnMut() -> bool + Send),
) -> Result<u16, String> {
    let deadline = Instant::now() + Duration::from_secs(120);
    while Instant::now() < deadline {
        if cancelled() {
            return Err(CLOSING.into());
        }
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
/// A failed or cancelled start stops what it already started, PostgreSQL included, cleanly.
pub async fn start<F, C>(
    runtime: &Path,
    data: &Path,
    update: F,
    mut cancelled: C,
) -> Result<Owner, String>
where
    F: FnMut(Status, Option<Backend>),
    C: FnMut() -> bool + Send,
{
    let mut timer = StartupTimer::start("app");
    let mut started = None;
    let result = start_stages(
        runtime,
        data,
        update,
        &mut timer,
        &mut started,
        &mut cancelled,
    )
    .await;
    let outcome = match &result {
        Ok(()) => "ok",
        Err(_) if cancelled() => "closed",
        Err(_) => "failed",
    };
    timer.save(data, outcome);
    match (result, started) {
        (Ok(()), Some(owner)) => Ok(owner),
        (Ok(()), None) => Err("Nie udało się uruchomić katalogu.".into()),
        (Err(message), Some(mut owner)) => {
            owner.stop().await;
            Err(message)
        }
        (Err(message), None) => Err(message),
    }
}

async fn start_stages<F>(
    runtime: &Path,
    data: &Path,
    mut update: F,
    timer: &mut StartupTimer,
    started: &mut Option<Owner>,
    cancelled: &mut (dyn FnMut() -> bool + Send),
) -> Result<(), String>
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
    let llama_token = random_secret()?;
    let desktop_token = random_secret()?;
    let owner = started.insert(Owner {
        children: Vec::new(),
        job: Job::new()?,
        pg_ctl: runtime.join("postgres/bin/pg_ctl.exe"),
        db: db.clone(),
        backend: None,
        python_port: 0,
        python_token: python_token.clone(),
        llama: None,
        llama_token,
        runtime: runtime.to_path_buf(),
        session: session.clone(),
        java: None,
        java_restarts: 0,
    });
    update(
        Status {
            phase: "Przygotowujemy prywatną bazę PostgreSQL…".into(),
            ..Status::default()
        },
        None,
    );
    timer.mark("prepare");
    if cancelled() {
        return Err(CLOSING.into());
    }
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
        if cancelled() {
            return Err(CLOSING.into());
        }
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
    if cancelled() {
        return Err(CLOSING.into());
    }
    update(
        Status {
            phase: "Uruchamiamy backend i sprawdzamy migracje…".into(),
            ..Status::default()
        },
        None,
    );
    owner.java = Some(JavaLaunch {
        data: data.to_path_buf(),
        pg_port,
        password,
        token: desktop_token,
    });
    // JVM start, Spring context, Flyway migrations and the identity check; java.log has the split.
    let backend = owner
        .start_backend(&session.join("java.json"), cancelled)
        .await?;
    timer.mark("backend");
    update(
        Status {
            phase: "Katalog gotowy. AI uruchomisz, gdy będzie potrzebne.".into(),
            backend_ready: true,
            analysis_ready: false,
            analysis_starting: false,
            error: None,
        },
        Some(backend),
    );
    Ok(())
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

#[cfg(test)]
mod tests {
    use super::has_gpu;

    #[test]
    fn a_listed_vulkan_device_means_the_model_runs_on_the_gpu() {
        assert!(has_gpu(
            "Available devices:\n  Vulkan0: AMD Radeon RX 6600 (8176 MiB, 7378 MiB free)\n"
        ));
        assert!(!has_gpu("Available devices:\n"));
        assert!(!has_gpu(""));
    }
}
