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

## Saved catalog

`App.tsx` reads `GET /api/catalog/page` with `folderPath`, `pageNumber` and
`pageSize`. The default page size is 50. The response contains `files`,
`pageNumber`, `pageSize`, `totalFiles` and `hasNext`; React keeps one page object
and derives its file list instead of storing a second copy.

Previous/next buttons change the requested page number. They are disabled at
the relevant boundary and while a request or scan is running. The UI displays
page numbers from 1, while the API counts from 0. A successful scan resets the
catalog to page 0 and reloads it. Choosing a folder changes the scan input;
the displayed catalog switches to that folder after the scan succeeds.

The effect ignores responses from superseded requests. The remembered last
folder uses the existing `screensort.lastFolder` local storage key; it is not
a database table. Pagination does not start a scan or change source images.

## Verification

```powershell
npm.cmd run lint
npm.cmd run build
```

To exercise both navigation buttons with the two demo records, temporarily
change `CATALOG_PAGE_SIZE` in `App.tsx` to 1, then reload the UI and move forward
and back. Restore 50 before committing. This checks saved records and requires
no new scan if the demo folder is already cataloged.

This is a development shell, not a distributable installer yet. Bundling and
launching Java, routing production API requests, and replacing the Docker-only
database dependency for end users are separate upcoming steps. Tauri bundling is
disabled until those pieces work together.
