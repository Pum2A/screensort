# ScreenSort

## Mapa projektu do czytania

- [Diagramy obecnej architektury](docs/diagramy-architektury.md) i [PDF na telefon](output/pdf/screensort-architektura.pdf).
- [Następny etap i analiza wydajności](docs/nastepny-etap-i-wydajnosc.md) i [PDF na telefon](output/pdf/screensort-plan-wydajnosc.pdf).

ScreenSort is a privacy-first local application for turning unnamed screenshots into a searchable, reviewable and safely organized collection.

The application scans a user-selected folder, extracts visible text with OCR, analyzes visual content, proposes a new file name and category, and presents every suggestion for manual review. Approved files are copied to a separate output directory. Original files are never modified in the MVP.

## Status

Early safe-cataloging slice with a React frontend and persistent PostgreSQL catalog.

The Spring Boot application scans PNG, JPG and JPEG files without modifying the source directory. React starts a background scan through `POST /api/scan/jobs`, polls its ID, shows stages and counters for new, rescanned, unchanged and rejected files, and displays saved files from PostgreSQL through `GET /api/catalog/page`. The saved catalog loads up to 50 records per page as a circular-thumbnail grid. Selecting an image opens a centered modal with an uncropped preview and connected metadata branches, over a dimmed, lightly blurred gallery. Close it with the top-right X, Escape or a backdrop click; keyboard focus returns to the thumbnail. The retro PC font is bundled locally, with no external font requests. Images are read by catalog record ID with path checks and bounded in-memory PNG caching. The older Thymeleaf screen remains available at `/` as a prototype.

The backend also supports background scan jobs: `POST /api/scan/jobs` returns
HTTP 202 with an ID, and `GET /api/scan/jobs/{id}` reports the stage and counters.
Only one scan may run at a time, including legacy requests. Up to 20 lightweight
job snapshots are kept in memory; restarting Java loses those snapshots, not the
persisted catalog. React polls sequentially about once per second and refreshes
the gallery after COMPLETED. A separate rejection endpoint keeps up to 100 examples
per job, with the full count and a truncation flag. Lost connections can resume
status checks without starting another scan; reloading the UI loses current tracking.
See [the step-by-step Polish explanation](docs/scanjob-krok-po-kroku.md).

## MVP flow

```text
Select source folder
        -> discover image files
        -> calculate metadata and checksum
        -> extract text with OCR
        -> analyze visible content
        -> propose name, category and tags
        -> manually review suggestions
        -> search by extracted content
        -> approve selected files
        -> create verified copies in the output folder
```

## Non-negotiable principles

- Local-first processing.
- The source directory is read-only.
- No deletion or moving in the MVP.
- Every suggestion requires review before a copy is created.
- Existing destination files are never overwritten.
- A completed copy must have the same SHA-256 checksum as its source.
- Repeating a scan or operation must not produce uncontrolled duplicates.
- When safety cannot be verified, the application stops without modifying user data.

## Documentation

- [Product brief](docs/project-brief.md)
- [Initial architecture](docs/architecture.md)
- [Mapa obecnego projektu po polsku](docs/struktura-projektu.md)
- [Galeria i podglądy — co zmieniliśmy i dlaczego](docs/galeria-i-podglady.md)
- [Zadania skanowania — kod, wątki i API po polsku](docs/zadania-skanowania.md)
- [ScanJob krok po kroku — start, Consumer, nagłówki i React](docs/scanjob-krok-po-kroku.md)
- [Delivery roadmap](docs/roadmap.md)
- [Decyzje techniczne wyjaśnione po polsku](docs/decyzje-techniczne.md)
- [ADR-001: Read-only source and copy-first organization](docs/adr/ADR-001-copy-first.md)
- [ADR-002: React web UI inside a thin Java desktop shell](docs/adr/ADR-002-react-desktop-ui.md)
- [ADR-003: Tauri desktop shell instead of JavaFX](docs/adr/ADR-003-tauri-desktop-shell.md)
- [Cross-device development handoff](docs/device-handoff.md)

## Development

Requirements:

- Java 25;
- Node.js 20.19+ or 22.12+ for frontend development;
- Docker Desktop using Linux containers;
- Docker Compose.

Run the current tests with:

```powershell
.\mvnw.cmd clean verify
```

Run the application with the `local` profile. Spring Boot will start the PostgreSQL service from `compose.yaml` and supply its connection details to the application:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Open <http://localhost:8080/> and use `demo-images` for the first scan. The test fixture contains two valid PNG files and one ZIP file that should be rejected.

To run the new frontend development server:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open <http://127.0.0.1:5173/>. During development, Vite forwards `/api` requests to Spring Boot at `127.0.0.1:8080`.

Frontend verification (from `frontend`):

```powershell
npm.cmd run test
npm.cmd run build
npm.cmd run lint
```

Layout and scan-API/polling tests use the built-in Node.js test runner and the existing TypeScript
compiler; generated test files stay in the ignored `.test-output` directory.

Scan `demo-images` twice to check catalog persistence. Both scans should show two accepted PNG files and one rejected ZIP. From the project root, inspect the saved rows in the local PostgreSQL container:

```powershell
docker compose exec postgres psql -U screensort -d screensort -c "SELECT ar.canonical_path, fr.relative_path, fr.id, fr.last_seen_at FROM file_record fr JOIN allowed_root ar ON ar.id = fr.allowed_root_id ORDER BY ar.canonical_path, fr.relative_path;"
```

The second scan should keep the same file IDs while refreshing `last_seen_at`. Scanning never modifies the source images.

For a folder not previously cataloged, the summary reports two new images and
one rejected file. On an unchanged rescan it reports two unchanged images and
one rejected file: the stored SHA-256 values are reused without decoding or
hashing those images again. A different size or modification time triggers a
full rescan of that image. Matching metadata is an optimization, not proof that
the file contents are identical; safe copying will require fresh verification.

The credentials in `compose.yaml` are development-only and must not be reused outside the local environment.

## Current milestone

Source-folder safety validation, accepted/rejected scan results, SHA-256 calculation, and the React scan client are connected to a persistent PostgreSQL catalog. Rescanning the same folder updates existing file rows instead of inserting duplicates. Incremental scanning compares saved size and modification time before expensive processing. React shows a compact scan summary, thumbnail gallery and selected-image details. Manual review of suggestions, OCR and safe copying are not implemented yet.

`GET /api/catalog/files/{id}/thumbnail` produces a PNG with a maximum edge of 320 px; `/preview` uses 1024 px. No source file is written. Preview input is limited to 50 MiB, 40 million pixels and 20,000 px per edge; unsupported or unavailable previews show a fallback in the UI. Up to two decoders work concurrently, and the encoded-image cache is bounded to 128 entries / 16 MiB. The API binds to `127.0.0.1` by default. See the gallery guide for security limitations and memory costs beyond the cache.

Pagination is applied in the database query, not by slicing a full list in Java or React. The page API accepts `folderPath`, zero-based `pageNumber`, and `pageSize` from 1 to 50. After a successful scan, React reloads page 0 for that folder. The legacy `GET /api/catalog/files` endpoint remains available, but React no longer uses it. Scan results and the known-file map used by incremental scanning are not paginated by this change.

## Not in the first release

Kafka, microservices, automatic deletion, automatic moving, cloud AI, whole-disk scanning, PDF support, mobile applications, multi-user accounts and cross-device synchronization.
