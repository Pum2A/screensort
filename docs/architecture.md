# ScreenSort — Initial Architecture

Status: Draft v0.1

Update 2026-10-12: this is the initial design, not the complete current layout.
[ADR-004](adr/ADR-004-local-python-analysis-service.md) brings forward an independent
local Python analysis service. Its HTTP API remains health-only; standalone OCR
and shared image preparation are verified. Florence CPU and the combined workflow
also run offline; Java integration is not ready. Java remains a modular core, and the service must
not share its database or source-path API. ADR-004 supersedes the timing and
single-process assumptions for analysis below, not the read-only/copy-first rules.

## 1. Architectural goal

Build one deployable modular monolith whose business boundaries remain clear enough to extract an analysis worker later without rewriting the catalog, review or organization logic.

The MVP optimizes for safety, testability and delivery speed rather than distributed-system complexity.

## 2. System context

```mermaid
flowchart LR
    User[User] --> UI[Local review UI]
    UI --> App[ScreenSort application]
    App --> DB[(PostgreSQL catalog)]
    App --> OCR[Local OCR engine]
    App --> Vision[Local visual analyzer]
    App --> Source[Source directory - read only]
    App --> Output[Output directory - write allowed]
```

## 3. Primary processing flow

```mermaid
flowchart TD
    Select[Select allowed source] --> Scan[Discover supported files]
    Scan --> Fingerprint[Read metadata and calculate SHA-256]
    Fingerprint --> Analyze[Run OCR and visual analysis]
    Analyze --> Suggest[Suggest name, category, tags and confidence]
    Suggest --> Review[Manual review]
    Review --> Search[Searchable catalog]
    Review --> Approve[Explicit approval]
    Approve --> Copy[Verified copy through partial file]
    Copy --> History[Operation history]
```

## 4. Application modules

| Module | Responsibility |
|---|---|
| `catalog` | File identity, metadata, checksum, duplicates and lifecycle |
| `scanning` | Allowed-root validation and discovery of supported files |
| `analysis` | OCR, visual analysis and aggregation of analyzer results |
| `classification` | Category, tag, name and confidence suggestions |
| `review` | Manual corrections and approval state |
| `search` | Queries over names, OCR text, categories and tags |
| `organization` | Copy planning, execution, verification and recovery |
| `api` inside each implemented feature | Local REST API and HTTP/JSON boundary; the temporary Thymeleaf screen is in `scanning/prototype` |
| `desktop` | Future thin Tauri shell, Rust native bridge and folder picker |
| `shared` | Small cross-cutting primitives with no business ownership |

This is one Maven application at the start. Package boundaries are preferred over premature Maven modules.
The table also describes planned modules. For the **current** file layout, see
[the project map](struktura-projektu.md).

Current catalog UI: paginated records, PNG thumbnails by record ID and a selected
preview panel. `catalog/preview` performs source lookup, filesystem checks and
bounded in-memory image generation outside the database transaction. No original
images are written. The API binds to loopback by default. Exact limits, the current
flow and race/cache limitations are documented in [the gallery guide](galeria-i-podglady.md).

## 5. Dependency direction

```text
web -> application use cases -> domain
                              -> infrastructure ports

infrastructure adapters -> domain/application ports
```

Rules:

- Web controllers call use cases, not repositories.
- Domain objects do not depend on Spring MVC, OCR libraries or file-system implementations.
- OCR and visual analysis are replaceable adapters.
- Classification does not copy or move files.
- Only the organization module may create output files.
- Persistence entities are not returned directly to the UI.

## 6. Analysis pipeline

Each analyzer produces a partial result:

```text
MetadataAnalyzer
    -> dimensions, timestamps, extension

OcrAnalyzer
    -> extracted text, language, OCR confidence

VisualAnalyzer
    -> labels, optional description, visual confidence

AnalysisAggregator
    -> normalized analysis result

SuggestionService
    -> category, proposed name, tags, total confidence, explanation
```

An analyzer failure is recorded independently. For example, a screenshot with failed visual analysis may still be searchable through OCR.

## 7. File lifecycle

```mermaid
stateDiagram-v2
    [*] --> DISCOVERED
    DISCOVERED --> ANALYZING
    ANALYZING --> READY_FOR_REVIEW
    ANALYZING --> ANALYSIS_FAILED
    ANALYSIS_FAILED --> ANALYZING: retry
    READY_FOR_REVIEW --> APPROVED
    READY_FOR_REVIEW --> REVIEW_REQUIRED
    REVIEW_REQUIRED --> APPROVED
    APPROVED --> COPYING
    COPYING --> COPIED
    COPYING --> COPY_FAILED
    COPY_FAILED --> COPYING: retry
```

Review status and copy-operation status may become separate state machines when implementation begins. They are shown together here only to communicate the product flow.

## 8. Safe-copy protocol

```text
PLANNED
  -> SOURCE_VERIFIED
  -> SPACE_VERIFIED
  -> TARGET_PARTIAL_CREATED
  -> TARGET_HASH_VERIFIED
  -> TARGET_FINALIZED
  -> COMPLETED
```

On failure, the operation becomes `FAILED` or `RECOVERY_REQUIRED`. The original source remains untouched.

## 9. Initial data model

| Aggregate or record | Purpose |
|---|---|
| `AllowedRoot` | Canonical source path and access policy |
| `FileRecord` | Source identity, metadata, checksum and catalog state |
| `AnalysisJob` | Execution state, attempts and errors |
| `AnalysisResult` | OCR text, visual labels, proposed values and analyzer versions |
| `ReviewDecision` | User-corrected name, category and approval information |
| `CopyOperation` | Source, target, expected checksum and recovery state |
| `OperationAttempt` | Audit trail of individual attempts and errors |

The exact relational schema will be defined immediately before the first Flyway migration.

## 10. Filesystem boundary

The application maintains two distinct roots:

```text
source root: read-only
output root: controlled write access
```

Required checks:

- resolve canonical paths;
- reject drive roots, the entire user home and system locations;
- reject traversal outside the allowed root;
- do not follow symbolic links outside the root;
- verify that a file is stable before analysis;
- re-read source metadata and checksum before copying;
- never use overwrite semantics for user files.

## 11. UI boundary

The review UI is a local React and TypeScript single-page application built with Vite. Spring Boot owns filesystem access and business behavior and exposes JSON through a local REST API. During development, Vite runs on `127.0.0.1:5173` and proxies `/api` to Spring Boot on `127.0.0.1:8080`.

For the desktop release, a thin Tauri shell will display the built frontend in the operating system web view and provide the native Windows directory chooser through a deliberately narrow JavaScript-to-Rust command surface. The existing Thymeleaf screen remains a temporary working prototype while the REST boundary is introduced incrementally.

The first important product screen is the review catalog, not a dashboard.

Each item shows:

- thumbnail;
- original name;
- editable suggested name;
- editable category;
- OCR excerpt;
- visual tags;
- confidence and explanation;
- processing or copy status;
- selection control.

A normal browser page cannot be trusted to provide unrestricted local filesystem paths. Only the desktop shell may return the selected directory path, and the backend must still pass that path through `ScanFolderValidator`. The bridge must not expose arbitrary filesystem operations to frontend code.

## 12. Deployment profiles

| Profile | Purpose |
|---|---|
| `local` | Application from IDE with PostgreSQL in Docker Compose |
| `test` | Deterministic integration tests with temporary directories and Testcontainers |
| `demo` | Synthetic screenshot set, visible diagnostics and safe fixed roots |

The desktop launcher and distributed worker profiles are future decisions.

## 13. Planned evolution

Only after the complete MVP works locally:

1. automatic directory watching plus periodic reconciliation;
2. more capable local visual models;
3. PDF and document extraction;
4. native desktop packaging;
5. separate analysis worker;
6. Kafka, outbox, retry and dead-letter handling.
