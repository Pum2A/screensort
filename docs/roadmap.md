# ScreenSort — Delivery Roadmap

Status: Draft v0.1  
Assumed pace: 10–15 focused hours per week

Update 2026-10-11: [ADR-004](adr/ADR-004-local-python-analysis-service.md)
brings the narrow local Python analysis service into milestone 3. Its independently
runnable health-only scaffold exists. The original dates below are planning
estimates, not a claim that later milestones or analysis are completed.

## Working rule

Every milestone must finish with observable user behavior and relevant tests. Infrastructure or architectural layers alone do not count as a completed milestone.

## Milestone 0 — Product boundary

Target: before production code

### Deliverables

- Product brief.
- Initial architecture.
- MVP boundaries and measurable success criteria.
- ADR-001: read-only source and copy-first behavior.
- First implementation backlog.

### Exit criteria

- One primary user and workflow are defined.
- Supported formats and excluded features are explicit.
- Source and output safety rules are agreed upon.
- The first end-to-end technical slice is small enough to finish in one week.

## Milestone 1 — Project bootstrap

Target: week 1

### Scope

- Java and Spring Boot project.
- Maven build.
- PostgreSQL through Docker Compose.
- Flyway migrations.
- `local`, `test` and `demo` profiles.
- Basic package-by-feature structure.
- CI build and test workflow.
- Application health endpoint.

### Exit criteria

A new developer can clone the repository, start the required infrastructure and run all tests using documented commands.

## Milestone 2 — Safe cataloging slice

Target: weeks 2–3

### Scope

- `AllowedRoot` model.
- Canonical-path validation.
- PNG/JPG/JPEG discovery.
- Metadata extraction.
- SHA-256 calculation.
- Stable file identity and duplicate handling.
- Manual rescan.
- Minimal catalog screen.
- Tests for traversal, links, unsupported files and repeated scans.

### Exit criteria

The application scans a demo directory twice without changing source files or creating duplicate catalog records.

## Milestone 3 — OCR and analysis

Target: weeks 3–4

### Scope

- Local Polish and English OCR.
- Local Python analysis service with a versioned contract and Java-owned jobs;
  HTTP is health-only; standalone OCR and visual inference work, Java integration remains.
- Versioned analysis jobs and results.
- Visual analyzer interface.
- First local visual labeling implementation.
- Independent analyzer errors.
- Retry of failed analysis.
- Controlled labeled demo dataset.

### Exit criteria

The user can inspect OCR text and visual tags for every successfully analyzed screenshot, while failures remain visible and retryable.

## Milestone 4 — Suggestions, review and search

Target: weeks 5–6

### Scope

- Suggested file name.
- Suggested category and tags.
- Confidence and explanation.
- Thumbnail review list.
- Manual edits and approval.
- Search across names and OCR text.
- Filters for category, tags and status.

### Exit criteria

The user can find a screenshot by visible text, understand the suggestion and correct it before approval.

Begin applying for relevant backend and software-engineering roles no later than the completion of this milestone.

## Milestone 5 — Verified copy

Target: week 7

### Scope

- Separate output root.
- Copy plan and preview.
- Collision-safe target names.
- Free-space preflight.
- `.partial` copy.
- SHA-256 verification.
- Finalization without overwrite.
- Operation history and retry.

### Exit criteria

Approved files are copied to category directories, completed copies match source checksums and original files remain unchanged.

## Milestone 6 — Recovery and portfolio release

Target: weeks 8–10

### Scope

- Recovery of interrupted copy operations.
- Restart and failure tests.
- Processing time and failure metrics.
- Synthetic demo dataset.
- Architecture diagrams and selected ADRs.
- Installation and run instructions.
- Two-to-four-minute demonstration video.
- Tagged release.

### Exit criteria

- An interrupted copy has a deterministic recovery outcome.
- The full workflow can be demonstrated without private data.
- A reviewer can run the project without contacting the author.

## Deferred backlog

- Automatic filesystem watcher.
- PDF support.
- Custom categories and rules.
- Semantic search.
- Advanced local vision model.
- Tauri desktop shell and installer.
- Safe move with backup and restore.
- Kafka and distributed analysis workers (not the accepted narrow local service).
- Transactional outbox, retry and dead-letter topic.
- Prometheus and Grafana.

## First implementation backlog

1. Select exact project coordinates and supported Java/Spring Boot baseline.
2. Generate the Maven application.
3. Add PostgreSQL and Docker Compose.
4. Add Flyway and the first schema migration.
5. Create the `AllowedRoot` domain model.
6. Implement canonical-path validation.
7. Discover supported files without following unsafe links.
8. Calculate SHA-256 and persist `FileRecord`.
9. Make repeated scans idempotent.
10. Display a minimal file catalog.
11. Add filesystem unit tests.
12. Add PostgreSQL integration tests.
