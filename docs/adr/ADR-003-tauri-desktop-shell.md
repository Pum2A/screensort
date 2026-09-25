# ADR-003: Tauri desktop shell instead of JavaFX

Date: 2026-09-17
Status: Accepted

Supersedes the desktop-shell decision in
[ADR-002](ADR-002-react-desktop-ui.md).

## Context

ADR-002 chose a thin JavaFX shell to host the React frontend and provide the
native Windows directory chooser. At that time Tauri was rejected because it
adds Rust to the toolchain.

Since then two things changed the balance:

- The React frontend now exists and talks to the local Spring REST API. The UI
  is committed to web technologies, so a shell that natively embeds a web app is
  a better fit than one that embeds a web view as a secondary concern.
- The owner explicitly wants a modern, portfolio-relevant stack and is willing
  to learn a small amount of Rust. Rust is in demand and learning it, even
  partially, is a deliberate goal rather than an accidental cost.

The core requirement is unchanged: a normal browser cannot be trusted to reveal
absolute local filesystem paths, so a desktop shell with a native directory
chooser is required to let the user pick a folder.

## Decision

The desktop release will use **Tauri** as the shell instead of JavaFX.

Tauri will:

- bundle and display the built React frontend in a native web view;
- start or connect to the local Spring Boot application;
- open the native Windows directory chooser through Tauri's dialog plugin;
- pass the selected folder path to the backend through the existing local API.

Most UI code stays in React and TypeScript. Tauri's dialog plugin opens the
picker without a custom Rust command. Rust remains a thin desktop shell and may
later manage the bundled Java process. The backend stays
a Spring Boot modular monolith. This decision does not introduce microservices.

The selected path is untrusted input even when it comes from the native picker.
It must pass through `ScanFolderValidator` before `ImageScanner` can use it.

## Alternatives considered

### JavaFX (the previous choice)

Still a valid, actively maintained option in 2026 with a built-in
`DirectoryChooser`, and it keeps everything in the Java ecosystem. Rejected here
only because the owner prefers a modern web-native shell and wants exposure to
Rust. This is a preference-driven change, not a correction of a technical fault
in JavaFX.

### Electron

Mature and widely used commercially, but bundles a full Chromium and a separate
Node runtime, adding a second heavyweight runtime alongside Java. Larger
footprint and more moving parts than Tauri for a single-user local app.

### Plain browser (PWA / File System Access API)

Rejected: a browser page still cannot hand the backend an unrestricted absolute
folder path, which the scanning use case requires.

## Consequences

### Positive

- Modern, portfolio-relevant desktop stack built around the existing React UI.
- Small binaries and low memory footprint compared with Electron.
- Native folder selection without giving ordinary web pages unrestricted access.
- The owner gains introductory Rust experience through the native bridge.
- The existing Java scanning code remains the single owner of filesystem behavior.

### Negative

- Rust and the Tauri toolchain are added to the project.
- The Maven, npm and Tauri/Cargo builds must later be combined into one release
  pipeline.
- The JavaScript-to-Rust command surface becomes a security boundary.
- The chosen web view must be tested against the generated frontend.

## Sequencing

The owner chose to bring forward a narrow development shell so the native folder
picker can be exercised with the existing REST API. The first slice provides
the Tauri window and picker only. Java process management, production API
routing, a database that needs no Docker on an end-user machine, and installer
verification are separate steps. No installer is released before these work.

## Safety constraints

- Bind local servers to the loopback interface, not the public network.
- Load only trusted local ScreenSort content in the desktop web view.
- Do not expose general file read, write or command execution through the bridge.
- Keep the source directory read-only and retain all rules from ADR-001.
