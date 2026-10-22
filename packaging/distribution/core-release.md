## ScreenSort Windows x64 preview

New in 0.2.5: a reliability and privacy update. There are no new features, and
the workspace looks the same as in 0.2.4.

- One damaged image no longer stops a folder scan. It is skipped with the
  reason "Nie udało się odczytać obrazu" and the other files are still added
  to the catalog.
- Error logs of background scans, analyses and copies no longer contain file
  paths, and folder errors in the catalog no longer echo the folder path.
- The application checks every answer from its local services before showing
  it. Unexpected data now shows a short message instead of a broken screen,
  and server error details are never displayed.
- Scan, folder-analysis and copy progress keep updating while the window is
  minimized, so the catalog refreshes as soon as a scan finishes. Progress
  screens also send fewer background requests.

The recognition models, categories, naming rules and update channel are
unchanged. The catalog, saved analyses and approved decisions carry over.
0.2.4 introduced the redesigned workspace and update checks on every fresh
launch.

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
