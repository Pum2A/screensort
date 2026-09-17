# ADR-002: React web UI inside a thin Java desktop shell

Date: 2026-09-14  
Status: Accepted (desktop-shell choice superseded by [ADR-003](ADR-003-tauri-desktop-shell.md))

## Context

ScreenSort needs a modern, component-based review interface and a native Windows
directory chooser. A normal browser intentionally does not reveal arbitrary local
filesystem paths to a web application. Building the complete interface directly
with JavaFX would also discard the owner's existing frontend experience.

## Decision

The user interface will be built with React, TypeScript and Vite. Spring Boot
remains the local backend and exposes application behavior through a REST API.

The desktop release will use a thin JavaFX shell that:

- starts or connects to the local Spring Boot application;
- displays the built frontend in an embedded web view;
- opens the native directory chooser;
- exposes only a narrow, controlled bridge needed for desktop integration.

The selected path is untrusted input even when it comes from the native picker.
It must pass through `ScanFolderValidator` before `ImageScanner` can use it.

ScreenSort remains one modular monolith. This decision does not introduce
microservices or a separate remote backend.

## Development model

During development:

```text
React and Vite  -> http://127.0.0.1:5173
Spring Boot API -> http://127.0.0.1:8080
```

Vite proxies requests beginning with `/api` to Spring Boot. In a packaged build,
Vite produces optimized static assets that the desktop shell loads locally.

## Alternatives considered

### Continue with Thymeleaf

Useful for the first working prototype, but rejected as the final UI because the
review workspace will have significant client-side state and interaction.

### Build the entire UI in JavaFX controls

Rejected because it would require a second UI skill set and would not reuse the
owner's frontend experience.

### Electron or Tauri

Both can provide a modern web-based desktop shell. They are deferred because
Electron adds a separate Node desktop runtime and Tauri adds Rust to the core
toolchain. JavaFX keeps the native bridge in the same Java ecosystem as the
backend.

## Consequences

### Positive

- Reusable, state-driven React components for the catalog and review workflow.
- Compile-time checks in frontend code through TypeScript.
- Fast development and optimized production builds through Vite.
- Native folder selection without giving ordinary web pages unrestricted access.
- Existing Java scanning code remains the single owner of filesystem behavior.

### Negative

- Node.js is required for frontend development.
- Maven and npm builds must later be combined into one release pipeline.
- Communication between JavaScript and Java becomes a security boundary.
- The chosen embedded web view must be tested against the generated frontend.

## Safety constraints

- Bind local servers to the loopback interface, not the public network.
- Load only trusted local ScreenSort content in the desktop web view.
- Do not expose general file read, write or command execution through the bridge.
- Keep the source directory read-only and retain all rules from ADR-001.
