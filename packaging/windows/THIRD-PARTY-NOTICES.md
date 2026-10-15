# Bundled third-party components

This local prerelease bundles independent open-source components. Their original
license files and notices accompany the runtime; this document does not replace them.

* Eclipse Temurin/OpenJDK: GPLv2 with Classpath Exception. See `java/legal/` and
  [Adoptium source and notices](https://adoptium.net/temurin/releases/).
* CPython: PSF license; see `python/LICENSE.txt` and
  [CPython source](https://github.com/python/cpython/tree/v3.14.8).
* PostgreSQL: PostgreSQL license, plus EDB's binary distribution notices; see
  `postgres/server_license.txt`, `postgres/commandlinetools_3rd_party_licenses.txt`.
  [PostgreSQL source](https://www.postgresql.org/ftp/source/).
* Microsoft Visual C++ runtime: app-local DLLs from the build toolchain's
  official redistributable directory, not copies from Windows System32.
  Redistribution remains subject to the applicable Visual Studio/Build Tools
  license and [Microsoft's distribution list](https://learn.microsoft.com/visualstudio/releases/2022/redistribution).
* Python dependencies (including PyTorch, ONNX Runtime, RapidOCR, Transformers,
  FastAPI, NumPy and Pillow): original `python/Lib/site-packages/*.dist-info/`
  license/metadata directories and package notices are included.
* Florence-2-base-ft checkpoint: model card and license in
  `models/models--florence-community--Florence-2-base-ft/snapshots/`;
  pinned community conversion of Microsoft's Florence-2 model.
* Java libraries: Maven metadata/license notices inside `backend.jar` and its nested jars.
* Tauri, React and other frontend/native dependencies: see the checked-in Cargo
  and npm lockfiles. This prerelease is not a substitute for a distribution-license audit.

The exact Python, PostgreSQL and model pins are in `runtime-manifest.json`.
No user images or development database files are included.
