# ScreenSort frontend and desktop shell

React/TypeScript renders the interface. Tauri provides the Windows window and
native folder chooser. Java/Spring Boot still validates paths, scans images and
owns all filesystem logic. Selecting a folder does not modify its files.

## Development

Start Docker Desktop and the Spring Boot backend using the `local` profile, then
from this directory run:

```powershell
npm.cmd install
npm.cmd run tauri dev
```

Tauri starts the Vite development server and loads it in a desktop window. The
"Wybierz folder" button is visible only there. Select a folder and click "Scan
folder" to call the existing Java API. A browser-only fallback is available
with `npm.cmd run dev` at <http://127.0.0.1:5173/>; it accepts a typed path.
During development, Vite proxies `/api` to Spring Boot at
<http://127.0.0.1:8080/>.

## Verification

```powershell
npm.cmd run lint
npm.cmd run build
```

This is a development shell, not a distributable installer yet. Bundling and
launching Java, routing production API requests, and replacing the Docker-only
database dependency for end users are separate upcoming steps. Tauri bundling is
disabled until those pieces work together.
