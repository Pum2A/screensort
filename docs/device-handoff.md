# ScreenSort — Cross-device development handoff

Use this document when moving ScreenSort development to another computer or starting a new Codex conversation.

## Canonical repository

The canonical repository is intended to be:

~~~text
https://github.com/Pum2A/screensort
~~~

It is private. Authenticate Git or IntelliJ with the GitHub account that owns or can access the repository.

The older planning repository at **<lokalny-katalog-projektu> is a backup only. Do not copy its **.git** directory and do not treat it as the canonical project.

## Laptop prerequisites

- Git.
- Java 21 LTS.
- Docker Desktop with WSL2 and Linux containers.
- IntelliJ IDEA.

Maven does not need to be installed globally because the repository contains the Maven Wrapper.

## First setup on the laptop

Open PowerShell in the directory where you keep projects:

~~~powershell
git clone https://github.com/Pum2A/screensort.git
cd screensort
.\mvnw.cmd clean verify
~~~

The verification command starts an isolated PostgreSQL container through Testcontainers, applies Flyway migrations and runs all tests.

Start the application:

~~~powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
~~~

The **local** profile starts PostgreSQL from **compose.yaml**. Check:

- <http://localhost:8080/actuator/health>
- <http://localhost:8080/actuator/info>

Stop the application with **Ctrl+C**.

## Keeping both computers synchronized

Before switching computers:

~~~powershell
git status
git add -A
git commit -m "Describe the completed change"
git push
~~~

On the other computer, before editing:

~~~powershell
git status
git pull --ff-only
~~~

Do not work on uncommitted versions of the same files on both computers. Commit and push on one device, then pull on the other.

## Current project state

- Java 21.
- Spring Boot 4.1.1.
- Maven Wrapper using Maven 3.9.16.
- PostgreSQL through Docker Compose for local development.
- PostgreSQL through Testcontainers for integration tests.
- Flyway owns database schema changes.
- Hibernate validates mappings and does not create or modify the schema.
- Application package: **pl.pum.screensort**.
- Architecture: one package-by-feature modular monolith.
- Feature packages have documented domain, application, port and infrastructure boundaries.
- Current milestone: Milestone 2 — safe cataloging.
- Current verification: 12 tests passing, 0 failures, 0 errors and 0 skipped.

Implemented:

- normalized ScreenSort project coordinates;
- **local**, **test** and **demo** profiles;
- Actuator health and info endpoints;
- Java 21 GitHub Actions workflow;
- immutable **AllowedRoot** domain value;
- canonical source-root validation;
- rejection of filesystem roots, the entire user home and protected Windows locations;
- rejection of path traversal and links escaping the allowed root;
- idempotent **AllowedRoot** persistence through Spring Data JPA;
- Flyway migrations V1 and V2.
- complete package skeleton for the documented application modules.

## Non-negotiable safety rules

- The selected source directory is read-only.
- Never rename, modify, move or delete source screenshots.
- Never overwrite destination files.
- Do not scan drive roots, the whole user home or system locations.
- Do not follow filesystem links outside the allowed root.
- Copy operations require explicit user approval.
- A future completed copy must match the source SHA-256 checksum.
- If safety cannot be verified, stop without modifying user data.

Read these files before implementing new behavior:

- **README.md**
- **docs/project-brief.md**
- **docs/architecture.md**
- **docs/package-structure.md**
- **docs/roadmap.md**
- **docs/adr/ADR-001-copy-first.md**

## Next implementation slice

Implement safe PNG/JPG/JPEG discovery:

1. Discover supported files from an **AllowedRoot**.
2. Keep recursive scanning disabled unless the stored policy enables it.
3. Do not follow directory links.
4. Resolve every discovered file through the existing **SourcePathResolver**.
5. Record path, original name, MIME type, size and modification time.
6. Add focused tests for unsupported extensions, traversal, directory links and recursive/non-recursive behavior.
7. Preserve the read-only-source rule.

Do not implement OCR, classification, UI review or copy operations in this slice.

## Prompt for a new Codex conversation

Copy the following into a new conversation:

~~~text
Continue building ScreenSort from the private GitHub repository currently open in this workspace.

Read README.md, docs/device-handoff.md, docs/project-brief.md, docs/architecture.md, docs/package-structure.md, docs/roadmap.md and docs/adr/ADR-001-copy-first.md before editing. Inspect Git status and all existing changes first.

I am learning Spring but know basic/intermediate Java. Work in English and briefly explain important Spring and architecture concepts.

Preserve the read-only-source and copy-first safety rules. Do not modify, move, rename or delete source screenshots.

The current milestone is Milestone 2. AllowedRoot registration, canonical-path validation, JPA persistence, Flyway V2 and path-safety tests are implemented. The last verified build had 12 tests passing with zero skipped.

Implement the next focused slice: safe PNG/JPG/JPEG discovery with metadata collection, configurable recursion disabled by default, no following of directory links, and tests. Run .\mvnw.cmd clean verify before finishing.
~~~
