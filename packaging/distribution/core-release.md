## ScreenSort Windows x64 preview

New in 0.2.4: a redesigned workspace, larger image cards, updated review and
startup screens, smoother interactions and accessible tooltips. The native
application checks for updates on each fresh launch, independently of the UI;
the current version/check result is visible in the header. Offline checks do not
block the catalog. Downloading and installing still require an explicit action.
Existing verified category copies and source-file protections remain unchanged.
This update does not change the recognition models or improve contextual naming.

Also in 0.2.4, a backend reliability review closed eight gaps found in the
code: source paths and SHA-256 hashes no longer reach the console log,
resuming a paused folder analysis reports a conflict instead of a server
error, the connection to the analysis service no longer leaves a thread
behind, a failed background job keeps its cause, an invalid folder path is a
bad request instead of a server error, an organized copy claims its file under
a database lock so two instances cannot copy it twice, the unpaginated catalog
read is limited to 1000 records, and the scanner recognises an image from its
header instead of decoding the whole file into memory.

One visible change: a file named .png that actually contains a GIF is no
longer added to the catalog, because the preview could not display it
anyway. The updater channel and the signed artifacts are unchanged in
structure; the version is bound to this release.

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
