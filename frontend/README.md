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
"Wybierz folder" button is visible only there. Select a folder and click "Skanuj
folder" to call the existing Java API. A browser-only fallback is available
with `npm.cmd run dev` at <http://127.0.0.1:5173/>; it accepts a typed path.
During development, Vite proxies `/api` to Spring Boot at
<http://127.0.0.1:8080/>.

## Saved catalog

`catalog/api.ts`, called by `App.tsx`, reads `GET /api/catalog/page` with `folderPath`, `pageNumber` and
`pageSize`. The default page size is 24. The response contains `files`,
`pageNumber`, `pageSize`, `totalFiles` and `hasNext`; React keeps one page object
and derives its file list instead of storing a second copy.

Previous/next buttons change the requested page number. They are disabled at
the relevant boundary and while a request or scan is running. The UI displays
page numbers from 1, while the API counts from 0. A successful scan resets the
catalog to page 0 and reloads it. Choosing a folder also opens its saved catalog
without starting a scan. In the browser fallback, use "Otwórz katalog" after typing
a path. A folder with no saved records stays empty until scanned.

The effect ignores responses from superseded requests. The remembered last
folder uses the existing `screensort.lastFolder` local storage key; it is not
a database table. Pagination does not start a scan or change source images.

## Gallery and selected preview

`catalog/CatalogGallery.tsx` renders the current page as compact tiles. Selection
stores only `selectedId`; the selected file is derived from this page.
`CatalogImage.tsx` loads lazy thumbnails by record ID, not by arbitrary paths.
`ImageDetailsDialog.tsx` requests the larger preview only after selection and
shows metadata from the last scan. Escape / the close button restore focus to
the selected tile. Technical details remain available in a disclosure section.

Thumbnail and preview URLs use `/api/catalog/files/{id}/thumbnail` and `/preview`.
Failed image loads show a fallback without removing catalog metadata. The larger
preview has a manual retry button. UI formatting uses binary KiB/MiB/GiB and
local Polish dates; exact bytes and UTC timestamps remain in technical details.
The responsive layout uses a side panel on desktop and a stacked panel on
narrow windows. No image URLs, fonts or icon packages are fetched from external
services. Full flow and limits: [gallery guide](../docs/galeria-i-podglady.md).

For an isolated development instance, set `SCREENSORT_BACKEND_URL` in the terminal
before starting Vite; the default remains `http://127.0.0.1:8080`. This variable
changes only the development proxy, not production/Tauri API routing.

## Verification

```powershell
npm.cmd run lint
npm.cmd run build
npm.cmd test
```

To exercise both navigation buttons with the two demo records, temporarily
change `CATALOG_PAGE_SIZE` in `App.tsx` to 1, then reload the UI and move forward
and back. Restore 24 before committing. This checks saved records and requires
no new scan if the demo folder is already cataloged.

The release build is a Windows NSIS installer built by Tauri (`bundle.active` in
`src-tauri/tauri.conf.json`). The Rust shell starts a private PostgreSQL, the Java
backend and the optional analysis service; the WebView only calls the allow-listed
`backend_request` / `backend_image` commands. Docker is needed only for development.
