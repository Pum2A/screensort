# ScreenSort — plan naprawczy

Źródło: audyt trzech stosów (backend Java, frontend + Tauri, analysis-service + infrastruktura), tylko odczyt, bez uruchamiania pełnych testów Javy/Tauri.
Stan: gałąź `codex/fluid-workspace-startup-updates`, wersja 0.2.4.

## 1. Co robi aplikacja

Lokalna aplikacja Windows do porządkowania zrzutów ekranu. Tauri 2 (Rust) uruchamia prywatny PostgreSQL 18, backend Spring Boot (Java 25, tylko 127.0.0.1) i opcjonalny serwis FastAPI z OCR (RapidOCR) i opisem obrazu (Florence-2). React renderuje katalog, skan, analizę, propozycje nazw i eksport kopii do kategorii. Pliki źródłowe nie są modyfikowane. WebView nie zna portów ani tokenów (allowlista IPC w Rust). Updater: minisign + HTTPS + przypięte URL-e.

## 1a. Stan zdrowia (zmierzony 2026-10-21)

| Stos | Linie kodu (src / testy) | Testy |
|---|---|---|
| Java (backend) | ok. 4 400 / 3 900 | `./mvnw clean verify`: 198 testów, 0 błędów, 2 pominięte |
| Rust (Tauri) | ok. 3 100 | `cargo test --lib`: 23 OK |
| Python (analiza) | ok. 940 / 1 070 | pytest: 102 OK, 2 pominięte (opt-in) |
| TypeScript (UI) | ok. 2 600 / 600 | lint, `tsc` strict, build, 58 testów OK |

Historia: 206 commitów od 2026-09-10 (74 feat, 44 docs, 24 fix, 17 refactor). W repo jest pięć gałęzi `codex/*` oraz `main` i `master`.

## 2. Ocena ogólna

Kod jest zdyscyplinowany: brak `any`/`@ts-ignore`, brak `System.out` w produkcji, SQL tylko parametryzowany, ochrona przed path traversal, tokeny per sesja. **Krytycznych luk nie znaleziono.** Główne ryzyka: (a) API zwraca dane, których CLAUDE.md zabrania, (b) kruchy cykl życia procesów w Rust, (c) pojedyncze błędy odporności backendu, (d) higiena repo/CI/zależności.

## 3. Lista zadań (od najważniejszych)

Legenda: [x] zrobione w tej sesji, [ ] otwarte, [?] wymaga decyzji właściciela.

### Wysokie

1. [?] **Prywatność API** — odpowiedzi zawierają ścieżki, nazwy plików, SHA-256 i tekst OCR (`CatalogFileDto`, `AcceptedImageDto`, `RejectedFileDto`, `ScanJob`, `BatchStore`, `ReviewItem`, `ExportStore`, `NamingReviewDto`, `AnalysisResult`). Decyzja: albo zmienić regułę w CLAUDE.md (UI faktycznie potrzebuje nazw), albo przejść na `fileId`. Dodać test regresji.
2. [x] **`IOException` w `CatalogRestController`/`ScanFolderValidator`** może wyciec ścieżkę do logu (500). Naprawione: `@ExceptionHandler(IOException.class)` w `CatalogRestController` zwraca stały komunikat i loguje tylko nazwę klasy wyjątku; test `ioFailureReturnsFixedMessageWithoutLeakingThePath`.
3. [x] **Jeden uszkodzony obraz przerywa skan** (`ImageScanner` nie łapie `RuntimeException` z ImageIO; podobnie `CatalogImagePreview`). `ImageScanner.isReadableImage` łapie teraz też `RuntimeException`; test z uszkodzonym PNG. Zostaje: `CatalogImagePreview` (podgląd) nadal nie mapuje `RuntimeException` na `INVALID_IMAGE`.
4. [ ] **Cykl życia procesów (Rust)**: zamknięcie okna w trakcie pierwszego initdb, `desktop.owner` trzymany do ~2 min podczas przygotowania AI, brak sprzątania PG przy błędzie startu, błąd instalatora zostawia aplikację z martwym backendem (`processes.rs`, `mod.rs`, `updates.rs`).
5. [x] ~~Brak lockfiles~~ — **fałszywy alarm subagenta**: `frontend/package-lock.json` i `frontend/src-tauri/Cargo.lock` są śledzone w git (sprawdzone `git ls-files`). Nieśledzony jest tylko `package-lock.json` w katalogu głównym (patrz #6).
6. [ ] **Pakiet npm `claude` w katalogu głównym** (`package.json`, nieśledzony, + `node_modules/`). To nie oficjalny `@anthropic-ai/claude-code`. Sprawdzić pochodzenie, usunąć. Nie uruchamiać install scripts.
7. [x] **`compose.yaml`**: Postgres wystawiony na wszystkie interfejsy, `latest`. Zmienione na `127.0.0.1::5432`, `postgres:18.6` (jak w Testcontainers), hasło z `POSTGRES_PASSWORD` z domyślnym `screensort`.

### Średnie

8. [?] **Workflow publikacji** (`windows-release.yml`): uruchamialny z dowolnej gałęzi, token bez ochrony środowiska. Wymaga ustawień w GitHub (Environment z reviewerami, ograniczenie do tagów `desktop-v*`, fine-grained token).
9. [ ] **Rust: `unwrap`/`expect`/`unreachable!`** w kodzie produkcyjnym (`mod.rs` 9×, `updates.rs` 12×, `packages/mod.rs`, `smoke.rs`). Regexy → `OnceLock`; mutexy → `Err`.
10. [ ] **Dane wrażliwe w błędach smoke** (`smoke.rs`: pełna odpowiedź analizy z OCR trafia do stderr i `report.json`).
11. [ ] **Ścieżka folderu i SHA-256 w query stringach** frontendu (`catalog/api.ts`, `organization/api.ts`, `review/state.ts`). Przenieść do body/ID; sprawdzić, czy backend nie loguje URI.
12. [x] **`tsconfig.app.json` bez `strict`** i lint bez reguł — dodane `strict: true` (kod już przechodził bez błędów) oraz `typescript/no-explicit-any` i `typescript/ban-ts-comment` w `.oxlintrc.json`. `no-non-null-assertion` świadomie pominięte (10 użyć, nie ma go w regułach projektu).
13. [ ] **Backend: globalny `@ControllerAdvice`** (jednolity `ErrorResponse`, handler `HttpMessageNotReadableException` — Jackson może cytować body), zamiast 6 rozproszonych handlerów.
14. [ ] **Profile dev**: brak tokenu/walidacji `Host` (DNS rebinding), `health.show-details=always` w demo.
15. [ ] **Pliki, które zniknęły z folderu**, zostają w katalogu (`CatalogUpdater`); potrzebna migracja V10 (`missing_since`) — skill `db-migration`.
16. [ ] **Eksport bez wznowienia** (`ExportStore.recover` bez endpointu resume).
17. [ ] **Wydajność**: N+1 przy zapisie katalogu (`CatalogUpdater`), wielokrotna walidacja folderu na jedną miniaturę.
18. [ ] **Frontend**: `DesktopStartup` odmontowuje całą aplikację przy chwilowym `backendReady=false` (utrata szkiców); współdzielony `abort` w `OrganizationPanel` gubi odpowiedzi mutacji; zwinięte gałęzie dalej wykonują zapytania.
19. [ ] **Integralność pakietu AI** weryfikowana przy starcie tylko markerem, nie hashami.
20. [ ] **Flagi `--smoke-test`/`--runtime-dir`** w binarium produkcyjnym → `cfg(debug_assertions)`.
21. [ ] **Testy**: brak testów `ExportManager`, kontrolerów, `launch.py`, ścieżek 429/503/504/408 serwisu Python, brak infrastruktury DOM w frontendzie; [x] ~~`System.out` w testach Javy~~ (usunięte z 4 klas; tabele w `CatalogEntityJpaTest` zamienione na asercje; zostaje opt-in `ImageScannerTimingTest`). Dodatkowo naprawiony NPE w `ScanRestController.handleValidation` (R6).
22. [ ] **Serwis Python po `MemoryError`**: `_ready` czyszczone na stałe do restartu procesu (`image/runtime.py`). Decyzja: restart workera czy świadome zachowanie.
23. [?] **`httpx2` w `analysis-service/pyproject.toml`** + `requests` używany w testach, a niezadeklarowany. Sprawdzić w `uv.lock`/PyPI (odczyt `*.lock` zablokowany regułą deny — zrobić ręcznie).
24. [x] **CI: brak `ruff format --check`** dla analysis-service (README to deklarował). Dodany; lokalnie przechodzi.
25. [ ] **CI Pythona**: tylko 3.14 na Linuksie (`requires-python >=3.12,<3.15`), brak `uv build`, brak pytest na Windows (platforma docelowa).
26. [ ] **Wersja w 3 miejscach** (`tauri.conf.json`, `Cargo.toml`, `App.tsx`); `/actuator/info` pokazuje wersję z pom. Jedno źródło + skrypt zgodności.

### Niskie / porządki

27. [ ] Notatki Codexa w kodzie (`ScanWorkflow.java:40-71`, `KnownFilesReader.java:17-78`) — usunąć (TODO.md pkt 2).
28. [ ] Martwy kod: `scanning/prototype/*` + `scan.html` + Thymeleaf, `ScanWorkflow.scanAndSave`, `AllowedRootEntity.recursiveScan`, [x] ~~`frontend/src/analysis/demo/*`~~ (usunięte razem z testem `analysisDemo.test.ts`), font VT323 i `--font-retro`, `OcrRecognizer.recognize`, parametr `languages`.
29. [ ] Duplikaty: reguły ścieżek i limity obrazów w 3 miejscach (`ManagedImagePath`/`ImageLimits`), focus-trap w 2 komponentach.
30. [ ] Współbieżność backendu: brak wykluczania skan↔batch/eksport, `synchronized` trzymany podczas HTTP, `connect` zamyka `HttpClient` w użyciu, `CREATE_NEW` pliku stanu po awarii.
31. [ ] Drobne błędy: `getFieldError()` null (NPE), `ValueError`→422 `INVALID_IMAGE` dla błędów opcji, `PythonAnalysisClient` mapuje każdy non-2xx na 503, `lang="en"` w `index.html`, blob zawsze `image/png`.
32. [ ] Ruff: rozszerzyć `select` o `B`, `T20`, `S`, `ASYNC` (obecnie `E,F,I,UP`); `print()` w `console.py` (CLI, świadome — udokumentować wyjątek w CLAUDE.md).
33. [ ] Hook `.claude/hooks/format-changed-file.mjs`: `spawnSync` z `shell:true` + ścieżka w cudzysłowach (wstrzyknięcie przez nazwę pliku). Użyć `shell:false`.
34. [ ] Sesje `sessions/<id>` bez retencji; `-Xmx512m` bez `ExitOnOutOfMemoryError`; hasło DB zapisywane nieatomowo.
35. [x] `.gitignore`: dodane `node_modules/`, `output/pdf/`. [x] Usunięta ścieżka snapshotu z `download_model.py`; [x] nieaktualny docstring `vision/__init__.py`; [x] README analysis-service (sekcje „Kolejne kroki” i „Nie dodano jeszcze…” przeczyły kodowi).
36. [ ] Nieaktualne dokumenty: [x] ~~`frontend/README.md`~~ (poprawione: page size 24, `ImageDetailsDialog`, instalator NSIS, `npm test`), [x] `lang="pl"` w `index.html`; otwarte: `docs/device-handoff.md:364-367`, `TODO.md` (`preparation.py:35` → linia 107), `V1` komentarz.
37. [ ] Dodać `form-action 'none'` do CSP; zweryfikować kanał updatera `updates-preview`; ACL/hash dla katalogu pakietów AI.

## 4. Rekomendowana kolejność

1. Decyzja właściciela w sprawie #1 (reguła prywatności vs. UI) — blokuje największy blok pracy.
2. Natychmiast i tanio: #6 (pakiet `claude`), #5 (lockfiles).
3. Odporność backendu: #2, #3, #13 razem (jedna paczka, wspólne testy).
4. Cykl życia Rust: #4, #9, #10.
5. Egzekwowanie reguł: #12, #32.
6. Reszta wg wartości; porządki (#27–#37) jako osobne małe commity.

## 5. Uwagi do prowadzenia prac

- Jeden logiczny commit na punkt, Conventional Commits po angielsku, PR do `main`.
- Gałąź `codex/fluid-workspace-startup-updates` może zawierać WIP właściciela — nic nie odrzucać.
- Zmiany schematu tylko przez nową migrację (skill `db-migration`).
- Ograniczenia audytu: bez uruchamiania testów Javy/Tauri/frontendu, bez odczytu `uv.lock`, `packaging/windows/ai-packages.json` i konfiguracji logowania backendu.
