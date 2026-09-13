# ScreenSort

ScreenSort is a privacy-first local application for turning unnamed screenshots into a searchable, reviewable and safely organized collection.

The application scans a user-selected folder, extracts visible text with OCR, analyzes visual content, proposes a new file name and category, and presents every suggestion for manual review. Approved files are copied to a separate output directory. Original files are never modified in the MVP.

## Status

Early safe-cataloging slice.

The Spring Boot application starts successfully and exposes a local Thymeleaf page for scanning a selected directory. The current scanner discovers valid PNG, JPG and JPEG files without modifying the source directory. Development proceeds in small, owner-understood slices.

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
- [Delivery roadmap](docs/roadmap.md)
- [ADR-001: Read-only source and copy-first organization](docs/adr/ADR-001-copy-first.md)
- [Cross-device development handoff](docs/device-handoff.md)

## Development

Requirements:

- Java 25;
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

The credentials in `compose.yaml` are development-only and must not be reused outside the local environment.

## Current milestone

Project bootstrap is complete and the first scanning flow is organized as a feature-based `scanning` module. The next planned work is to add a `ScannedImage` metadata model and introduce explicit source-folder safety validation.

## Not in the first release

Kafka, microservices, automatic deletion, automatic moving, cloud AI, whole-disk scanning, PDF support, mobile applications, multi-user accounts and cross-device synchronization.
