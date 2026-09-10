# ScreenSort

ScreenSort is a privacy-first local application for turning unnamed screenshots into a searchable, reviewable and safely organized collection.

The application scans a user-selected folder, extracts visible text with OCR, analyzes visual content, proposes a new file name and category, and presents every suggestion for manual review. Approved files are copied to a separate output directory. Original files are never modified in the MVP.

## Status

Milestone 2 — safe cataloging.

The project bootstrap is complete. ScreenSort can validate and persist safe source roots, then discover PNG, JPG and JPEG files with read-only metadata collection, configurable recursion and protection against directory links.

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
- [Package structure](docs/package-structure.md)
- [Delivery roadmap](docs/roadmap.md)
- [ADR-001: Read-only source and copy-first organization](docs/adr/ADR-001-copy-first.md)
- [Cross-device development handoff](docs/device-handoff.md)

## Development

Requirements:

- Java 21;
- Docker Desktop using Linux containers;
- Docker Compose.

Run the complete verification suite from PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Run the application with the `local` profile. Spring Boot starts the PostgreSQL service from `compose.yaml` and supplies its connection details to the application:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

While the application is running, its health is available at <http://localhost:8080/actuator/health>. The `test` profile disables Docker Compose because the integration test receives its database connection from Testcontainers. The `demo` profile includes `local` and shows detailed health information.

The credentials in `compose.yaml` are development-only and must not be reused outside the local environment.

## Current milestone

Milestones 0 and 1 are complete. Milestone 2 is active: allowed-root registration and safe image discovery are implemented. The next slice calculates SHA-256 fingerprints and persists idempotent catalog records. Publishing and verifying a fresh clone remain repository setup tasks.

## Not in the first release

Kafka, microservices, automatic deletion, automatic moving, cloud AI, whole-disk scanning, PDF support, mobile applications, multi-user accounts and cross-device synchronization.
