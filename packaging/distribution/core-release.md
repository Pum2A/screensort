## ScreenSort Windows x64 preview

New in 0.2.3: save reviewed images as an organized copy. Approve names and
categories, select a destination, and follow copying progress inside ScreenSort.
The app creates category folders, preserves existing files and checks each
source against its approved SHA-256 while copying. Original images stay in place.
Core updates can be downloaded and installed from ScreenSort.

The Core installer starts the local catalog and private PostgreSQL without installing Python or loading AI models. No Docker, JDK or system Python is required on the user's machine.

To enable analysis, open **Silnik analizy** inside the application, download it
or select the matching official ZIP, then start the engine. Downloaded packages
are checked against the SHA-256 pinned in this app version. Core updates do not
download these models again.

Current AI profile: CPU, RapidOCR and Florence-2-base-ft. Recognition and category/name suggestions are imperfect and require review. Only explicitly approved, current decisions are copied. Moving originals and alternative model/GPU profiles are not included.

Updater artifacts are cryptographically signed and bound to their app version.
This is separate from Windows Authenticode signing, which is not available yet.
Back up important application data before upgrading. Do not delete an existing
database to fix a startup error.
