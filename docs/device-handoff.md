# ScreenSort — cross-device and AI handoff

Last updated: 2026-09-14

Use this document when moving development to another computer or starting a new AI conversation.

## Canonical repository

The single canonical repository is the private GitHub repository:

~~~text
https://github.com/Pum2A/screensort
~~~

The default branch is `main`. Commit and push completed work before switching devices, then pull it on the other device before editing.

The older planning repository at `<lokalny-katalog-projektu> is a backup only. Do not modify or delete it yet, do not copy its `.git` directory, and do not treat it as the active project.

## Owner and collaboration preferences

- Speak with the project owner in Polish.
- The owner knows general programming and frontend concepts, but is learning Java and Spring.
- Explain unfamiliar Java and Spring syntax before expecting the owner to use it.
- Use frontend analogies when useful, for example controller as a route handler and model attributes as view props.
- Work in small, understandable slices. Do not generate many feature classes at once without agreement.
- The owner wants to write and understand core implementation code. Offer pseudocode and guidance first unless the owner explicitly asks the agent to implement something.
- Folder creation, mechanical refactoring, verification, Git commits and pushes may be handled by the agent when requested.
- Every coherent feature or fix should be tested, committed and pushed to `main`.

## Current technology

- Windows 11.
- Java 25 (Eclipse Temurin).
- Spring Boot 4.1.1.
- Maven Wrapper using Maven 3.9.16.
- PostgreSQL through Docker Compose for the `local` profile.
- React 19, TypeScript 6 and Vite 8 for the new frontend foundation.
- Spring MVC for the HTTP boundary and a temporary Thymeleaf scanning prototype.
- A thin JavaFX desktop shell and native folder picker are accepted but not implemented yet.
- JUnit Jupiter for tests.

Project coordinates:

~~~text
groupId:      pl.pum
artifactId:   screensort
application:  ScreenSort
base package: pl.pum.screensort
main class:   ScreenSortApplication
~~~

## Current implemented behavior

The project is no longer empty. The following vertical slice works:

1. `ScreenSortApplication` starts Spring Boot.
2. `GET /` renders `templates/scanning/scan.html`.
3. The page accepts a local folder path. It defaults to `demo-images`.
4. `POST /scan` calls `ImageScanner`.
5. `ImageScanner` reads only direct children of the selected directory; recursive scanning is not implemented.
6. It accepts only regular files without following symbolic links.
7. It accepts `.png`, `.jpg` and `.jpeg` names.
8. It rejects files larger than 50 MiB.
9. It uses `ImageIO.read(...)` to reject files that cannot be decoded as images.
10. Each accepted path becomes an immutable `ScannedImage` containing its path, original name, extension, byte size and modification time.
11. The page displays the accepted image metadata and formats file sizes as B, KB, MB or GB.
12. `ScanFolderValidator` converts an untrusted `Path` into an `AllowedRoot` before scanning.
13. The validator rejects missing paths, files, symbolic links, filesystem roots, the complete current user home and protected Windows locations.
14. `ImageScanner` and `ScanFolderValidator` are Spring-managed components connected through constructor injection.

The demo directory contains two accepted PNG files and one rejected ZIP file. Tests verify discovery, human-readable file sizes and folder safety rules.

The latest verified web behavior was:

~~~text
GET  /                    -> HTTP 200
POST /scan demo-images    -> HTTP 200, two PNG names displayed
Maven test suite          -> 7 tests, 0 failures, BUILD SUCCESS
~~~

The application currently does not persist scanned files, calculate SHA-256, detect duplicate content, run OCR, classify images or copy files.

A separate `frontend/` project now contains the future React user interface, a Vite development server on `127.0.0.1:5173` and an `/api` proxy to Spring Boot on `127.0.0.1:8080`. Its folder button is intentionally disabled because neither the REST scan endpoint nor the native JavaFX bridge has been implemented yet.

## Current files

~~~text
src/main/java/pl/pum/screensort/
|-- ScreenSortApplication.java
`-- scanning/
    |-- image/
    |   |-- ImageScanner.java
    |   `-- ScannedImage.java
    |-- safety/
    |   |-- AllowedRoot.java
    |   |-- InvalidScanFolderException.java
    |   `-- ScanFolderValidator.java
    `-- web/
        `-- ScanController.java

src/main/resources/templates/scanning/
`-- scan.html

src/test/java/pl/pum/screensort/scanning/
|-- image/
|   |-- ImageScannerTest.java
|   `-- ScannedImageTest.java
`-- safety/ScanFolderValidatorTest.java

frontend/
|-- src/
|   |-- App.tsx
|   |-- App.css
|   |-- index.css
|   `-- main.tsx
|-- package.json
|-- vite.config.ts
`-- README.md
~~~

The first working slice is organized in the `scanning` feature package. Do not create the remaining feature packages until they contain real code.

## Agreed architectural direction

Use a modular monolith organized by product feature, not global technical layers and not microservices.

Planned top-level feature packages:

~~~text
pl.pum.screensort
├── scanning
├── catalog
├── analysis
├── classification
├── review
├── search
└── organization
~~~

Each feature owns its controller, service, models and persistence code when those elements are actually needed. Do not create empty packages or speculative interfaces.

Important rules:

- `ScreenSortApplication` only starts the application; it is not the business workflow.
- Controllers handle HTTP input and prepare view data, but do not access the database or filesystem directly.
- Services coordinate a feature's use case.
- Repositories only handle persistence.
- Database entities are not returned directly to templates.
- Introduce interfaces where replacement is real, especially OCR and visual analyzers, not for every class by default.
- Only the future `organization` feature may write output files.

## Next agreed implementation steps

Do not jump directly to OCR or database persistence.

1. Add a richer `ScanResult` containing accepted and rejected entries with reasons.
2. Add MIME type to the scanned metadata.
3. Add SHA-256 and duplicate detection.
4. Persist catalog records in PostgreSQL and make repeated scans idempotent.
5. Expose the safe scan use case as a local REST endpoint and connect the React UI.
6. Add the thin JavaFX shell and native folder picker before the user-facing release.

The owner wants to continue writing core feature code after the mechanical folder-validation refactor. Explain the new files before starting another feature.

## Non-negotiable filesystem safety

- Treat the selected source directory as read-only.
- Never rename, modify, move or delete source files.
- Never overwrite destination files.
- Do not scan drive roots, the entire user home or system locations.
- Do not follow filesystem links outside the allowed source root.
- Future copy operations require explicit user approval.
- A completed copy must match the source SHA-256 checksum.
- If safety cannot be verified, stop without modifying user data.

Read these files before changing behavior:

- `README.md`
- `docs/project-brief.md`
- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/adr/ADR-001-copy-first.md`
- `docs/adr/ADR-002-react-desktop-ui.md`

## Laptop setup

Prerequisites:

- Git;
- Java 25;
- Docker Desktop using WSL2/Linux containers;
- IntelliJ IDEA.

Clone the private repository after authenticating Git with the `Pum2A` GitHub account:

~~~bash
git clone https://github.com/Pum2A/screensort.git
cd screensort
git status
./mvnw clean verify
~~~

Start the application from Git Bash:

~~~bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
~~~

Open:

- <http://localhost:8080/>
- <http://localhost:8080/actuator/health>

The `local` profile starts PostgreSQL through `compose.yaml`. Stop the application with `Ctrl+C`.

Before switching computers:

~~~bash
git status
git add .
git commit -m "Describe the completed change"
git push
~~~

On the other computer, before editing:

~~~bash
git status
git pull --ff-only
~~~

Do not edit the same uncommitted files independently on both computers.

## Prompt for a new AI conversation

Copy this prompt into a new conversation:

~~~text
Continue helping me build ScreenSort from the repository currently open in the workspace.

Before editing, read README.md, docs/device-handoff.md, docs/project-brief.md, docs/architecture.md, docs/roadmap.md, docs/adr/ADR-001-copy-first.md and docs/adr/ADR-002-react-desktop-ui.md. Inspect Git status and existing changes. The canonical private repository is https://github.com/Pum2A/screensort and the default branch is main.

Talk to me in Polish. I understand general programming and frontend concepts, but I am learning Java and Spring. Explain important syntax and use frontend analogies where useful. I want to understand and write core feature code myself, so proceed in small steps and do not generate many classes without discussing them first.

Preserve the read-only-source and copy-first safety rules. Never modify, move, rename or delete source screenshots. Test every coherent feature or fix, then commit and push it.

The current working slice starts Spring Boot, validates a user-selected folder into an AllowedRoot, scans its direct children for valid PNG/JPG/JPEG files, creates ScannedImage metadata and renders it in a temporary Thymeleaf view. A separate React 19, TypeScript 6 and Vite 8 frontend foundation exists in frontend/. The accepted UI direction is React talking to a local Spring REST API, later displayed inside a thin JavaFX desktop shell with a native folder picker. The next backend step is a richer ScanResult with accepted and rejected entries and reasons.
~~~
