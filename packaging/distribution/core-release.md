## ScreenSort Windows x64 preview

0.3.1 fixes 0.3.0: the library, "Do decyzji" and Duplikaty screens stayed
empty in the installed app. They show your folder again. Nothing else changed,
and no data was lost: the catalog, analyses and decisions were never touched.
If a screen cannot load its data, it now shows the reason instead of an
endless loading placeholder.

New in 0.3.0: a new look and faster bulk decisions.

- New design: a sidebar with Biblioteka, Do decyzji and Duplikaty, a light
  layout with one lime accent for AI results and progress, and offline fonts.
- The library shows the whole folder on one screen, with filters by AI
  category, a "certain proposals" card that approves them all in one click,
  and decision progress (ready / certain / to check).
- Select many images (click, Shift+click for a range, Ctrl+A) and approve,
  skip or set a category for all of them from the floating bar. A doubtful or
  missing name is never approved in bulk; open that image instead.
- New Duplikaty view for byte-identical files: keep one copy, and the others
  are skipped in the organized copy. Originals on disk are never touched.
- "Do decyzji" lists every proposal of the folder at once instead of pages of
  50, with a category pill on each card (keys 1-5) and the editor beside it.
- The AI engine download resumes after a dropped connection instead of
  starting again.
- Clicking an image opens its preview with the analysis and name editor that
  used to sit in the side panel.

The library and bulk review handle folders of up to 1000 images; analysis of
bigger folders still works. The recognition models, categories, naming rules
and update channel are unchanged. The catalog, saved analyses and approved
decisions carry over.

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
