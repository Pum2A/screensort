# ScreenSort

ScreenSort is a privacy-first local application for turning unnamed screenshots into a searchable, reviewable and safely organized collection.

The application scans a user-selected folder, extracts visible text with OCR, analyzes visual content, proposes a new file name and category, and presents every suggestion for manual review. Approved files are copied to a separate output directory. Original files are never modified in the MVP.

## Status

Learning reset — empty Java workspace.

All Java production code, tests and the pre-created package structure have intentionally been removed. The Maven build definition, dependencies, runtime configuration and product documentation remain as a starting point for a guided implementation.

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

- Java 21;
- Docker Desktop using Linux containers;
- Docker Compose.

The Java source and test directories are intentionally empty. After recreating the Spring Boot main class and the first tests, use the following command for verification:

```powershell
.\mvnw.cmd clean verify
```

After recreating the application entry point, run it with the `local` profile. Spring Boot will start the PostgreSQL service from `compose.yaml` and supply its connection details to the application:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The `local`, `test` and `demo` configuration files are retained for later use, but there is currently no Java application to start.

The credentials in `compose.yaml` are development-only and must not be reused outside the local environment.

## Current milestone

Product planning is complete, but implementation has intentionally returned to zero Java code. Development will restart with one small class at a time, written and understood by the project owner before the next step is added.

## Not in the first release

Kafka, microservices, automatic deletion, automatic moving, cloud AI, whole-disk scanning, PDF support, mobile applications, multi-user accounts and cross-device synchronization.
