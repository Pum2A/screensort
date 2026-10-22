IMPORTANT - TRYB KONWERSACJI: Lubię z Tobą luźno rozmawiać, pytać o architekturę i analizować pomysły. Gdy zadaję pytanie ('czemu coś tak działa', 'co o tym myślisz', 'co robimy dalej', 'zrób analizę'), odpowiadaj krótko i konkretnie jak partner do dyskusji:
1. NIE uruchamiaj od razu skryptów w Bashu ani nie edytuj kodu, dopóki wyraźnie o to nie poproszę.
2. Jeśli musisz sprawdzić kod, aby mi odpowiedzieć, użyj subagenta `explorer` (na modelu Haiku) albo przeczytaj tylko 1-2 najważniejsze pliki zamiast skanować całe repozytorium.

# ScreenSort

Local-first Windows desktop app: Spring Boot backend (`src/`), React + Tauri shell (`frontend/`),
Python FastAPI analysis service (`analysis-service/`). Feature folders mirror each other across stacks.

IMPORTANT: Always verify your work (test/typecheck) before finishing a task. Use the `explorer`
subagent to search the code and `verifier` for tests. Prefer running a single test over a whole suite.
Use `reviewer` for a second opinion on a finished diff.

## Commands (repo root unless noted; Git Bash on Windows)
- Java single test: `./mvnw -q test -Dtest=ClassName#method`. Compile only: `./mvnw -q -DskipTests test-compile`
- Java full check (as CI): `./mvnw --batch-mode clean verify`
- Frontend (in `frontend/`): `npm run lint` · `npm run build` (runs `tsc -b`) · `npm run check:previews`
- Frontend single test: `npx tsc -p tsconfig.tests.json && node --test .test-output/tests/<name>.test.js`
- Tauri single test: `cargo test --locked --manifest-path frontend/src-tauri/Cargo.toml --lib <name>`
- Tauri format check: `cargo fmt --manifest-path frontend/src-tauri/Cargo.toml -- --check`
- Python (in `analysis-service/`): `uv run --no-sync pytest tests/<file>.py::<test>` · `uv run --no-sync ruff check .`
- Local DB: `docker compose up -d` (Postgres). `compose.observability.yaml` adds Prometheus and Redis.

## Gotchas
- Java tests use Testcontainers: Docker Desktop must be running, or every DB test fails at startup.
- `cargo test` for Tauri needs `frontend/dist` (run `npm run build` first) and an existing
  `frontend/src-tauri/runtime/` folder (CI creates it empty).
- Frontend tests are not Vitest/Jest: `tsc` compiles to `.test-output/`, then `node --test` runs them.
- Python must be installed with `uv sync --locked --extra vision --group dev` before `--no-sync` works.
- Java has no formatter or checkstyle in the build. Match the surrounding style (2-space indent).
- A PostToolUse hook formats/lints each edited file (rustfmt, oxlint, ruff). Exit 2 means fix the reported lint error.
- Version authority is `frontend/src-tauri/tauri.conf.json` + `Cargo.toml`; pom/npm/Python versions are not used for releases.

## Hard rules
- Privacy: never log or return file paths, filenames, SHA-256 hashes, OCR text or raw HTTP headers.
  That includes exception messages and stack traces from file IO. Log IDs, counters and reasons.
  Reference: `diagnostics/ProductMetrics`.
- No `System.out`/`System.err`/`printStackTrace`; use SLF4J (pattern: `ScanJobManager`). Python: `logging`.
- Frontend: no `any`, `@ts-ignore` or lint disables. Rust: no `unwrap()` outside tests.
- Never edit an applied Flyway migration. See the `db-migration` skill.

## Workflow
- Conventional Commits in English, one logical change per commit, body explains why. PR to `main`.
- The branch `codex/fluid-workspace-startup-updates` may hold the owner's uncommitted WIP. Never discard it.
- Shipping a desktop release: the `desktop-release` skill. Writing a review doc: the `review-doc` skill.
- Reply to the owner in Polish; code, identifiers and commits stay in English.
