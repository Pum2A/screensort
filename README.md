# ScreenSort

## Mapa projektu do czytania

- [Diagramy obecnej architektury](docs/diagramy-architektury.md) i [PDF na telefon](output/pdf/screensort-architektura.pdf).
- [Następny etap i analiza wydajności](docs/nastepny-etap-i-wydajnosc.md) i [PDF na telefon](output/pdf/screensort-plan-wydajnosc.pdf).

ScreenSort is a privacy-first local application for turning unnamed screenshots into a searchable, reviewable and safely organized collection.

The application scans a user-selected folder, extracts visible text with OCR, analyzes visual content, proposes a new file name and category, and presents every suggestion for manual review. Approved files are copied to a separate output directory. Original files are never modified in the MVP.

## Status

Early safe-cataloging slice with a React frontend and persistent PostgreSQL catalog.

The Spring Boot application scans PNG, JPG and JPEG files without modifying the source directory. React can start a scan through `POST /api/scan` and display saved files from PostgreSQL through `GET /api/catalog/files`. The older Thymeleaf screen remains available at `/` as a prototype.

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

Scan `demo-images` twice to check catalog persistence. Both scans should show two accepted PNG files and one rejected ZIP. From the project root, inspect the saved rows in the local PostgreSQL container:

```powershell
docker compose exec postgres psql -U screensort -d screensort -c "SELECT ar.canonical_path, fr.relative_path, fr.id, fr.last_seen_at FROM file_record fr JOIN allowed_root ar ON ar.id = fr.allowed_root_id ORDER BY ar.canonical_path, fr.relative_path;"
```

The second scan should keep the same file IDs while refreshing `last_seen_at`. Scanning never modifies the source images.

The credentials in `compose.yaml` are development-only and must not be reused outside the local environment.

## Current milestone

Source-folder safety validation, accepted/rejected scan results, SHA-256 calculation, and the React scan client are connected to a persistent PostgreSQL catalog. Rescanning the same folder updates existing file rows instead of inserting duplicates. React now shows the saved catalog records; manual review, OCR and safe copying are not implemented yet.

## Not in the first release

Kafka, microservices, automatic deletion, automatic moving, cloud AI, whole-disk scanning, PDF support, mobile applications, multi-user accounts and cross-device synchronization.
