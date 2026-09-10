# ScreenSort — Cross-device development handoff

Use this document when moving ScreenSort development to another computer or starting a new Codex conversation.

## Canonical repository

The future canonical repository is intended to be:

~~~text
https://github.com/Pum2A/screensort
~~~

It has not been published yet because this local repository has no configured Git remote. When it is created, keep it private and authenticate Git or IntelliJ with the owning GitHub account.

The older planning repository at **<lokalny-katalog-projektu> is a backup only. Do not copy its **.git** directory and do not treat it as the canonical project.

## Laptop prerequisites

- Git.
- Java 21 LTS.
- Docker Desktop with WSL2 and Linux containers.
- IntelliJ IDEA.

Maven does not need to be installed globally because the repository contains the Maven Wrapper.

## First setup on the laptop after publication

Open PowerShell in the directory where you keep projects:

~~~powershell
git clone https://github.com/Pum2A/screensort.git
cd screensort
.\mvnw.cmd clean verify
~~~

The Java implementation and tests are currently empty, so full verification is not expected to work until the Spring Boot entry point is recreated.

After recreating the application entry point, start the application:

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
- Maven dependencies for Spring MVC, Thymeleaf, JPA, Validation, Actuator, Flyway, PostgreSQL and Testcontainers are retained.
- Docker Compose and the **local**, **test** and **demo** configuration files are retained.
- **src/main/java** contains no Java code.
- **src/test/java** contains no Java tests.
- No package architecture is currently imposed on the implementation.
- The architecture document is a future product direction, not a description of current code.

Preserved starting material:

- normalized ScreenSort project coordinates;
- **local**, **test** and **demo** profiles;
- Actuator configuration;
- Java 21 GitHub Actions workflow;
- Maven Wrapper;
- PostgreSQL development configuration;
- Flyway migration files from the previous implementation, to be reviewed before reuse;
- product brief, roadmap, architecture and safety ADR.

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
- **docs/roadmap.md**
- **docs/adr/ADR-001-copy-first.md**

## Next implementation step

Restart from one small, owner-written Java class. Explain syntax before introducing Spring annotations, interfaces, architectural layers or filesystem traversal. Do not add feature code without the project owner's explicit approval.

## Prompt for a new Codex conversation

Copy the following into a new conversation:

~~~text
Continue teaching me while I build ScreenSort in the repository currently open in this workspace.

Read README.md, docs/device-handoff.md, docs/project-brief.md, docs/architecture.md, docs/roadmap.md and docs/adr/ADR-001-copy-first.md before editing. Inspect Git status and all existing changes first.

Talk to me in Polish. I am learning both Java and Spring. I want to write the implementation myself, one small step at a time.

Preserve the read-only-source and copy-first safety rules. Do not modify, move, rename or delete source screenshots.

The Java production and test directories are intentionally empty. Do not generate an architecture or multiple classes for me. First discuss one small class in pseudocode, explain every new Java construct and wait for my approval or my own edit before proceeding.
~~~
