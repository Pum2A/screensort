# ScreenSort frontend

This directory contains the modern ScreenSort user interface built with React,
TypeScript and Vite. The Java/Spring Boot application remains the backend and
owns filesystem access, validation and scanning.

## Development

Install dependencies once:

```powershell
npm.cmd install
```

Start the frontend development server:

```powershell
npm.cmd run dev
```

Open <http://127.0.0.1:5173/>. Requests beginning with `/api` are proxied to the
Spring Boot application at <http://127.0.0.1:8080/>.

## Verification

```powershell
npm.cmd run lint
npm.cmd run build
```

The current screen confirms that the frontend toolchain works. It is not yet
connected to the scanner, and its folder button intentionally remains disabled
until the REST boundary and native folder picker are implemented.
