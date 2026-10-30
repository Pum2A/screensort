## ScreenSort Windows x64 preview

New in 0.4.0: a local Qwen model names your screenshots, and one button does the rest.

- New AI engine profiles "Qwen 3.5 4B" and "Qwen 3.5 2B" in "Silnik AI". The model looks
  at each screenshot and says which app it shows, what it is about and its category, so a
  name reads like `discord_planowanie_wyjazdu` instead of loose words. It runs on your
  graphics card when one is found (about 2 s per image), otherwise on the processor.
  The llama.cpp server and the model are downloaded from their official sources and every
  file is checked against a pinned SHA-256. Nothing leaves your computer.
- The existing OCR package keeps reading text from images when it is installed; if Qwen
  cannot start, it does the whole analysis as before.
- No separate scan step: a chosen folder is read at once, and the last folder again when
  the app opens, so new screenshots simply appear.
- One "Uporządkuj z AI" button starts the AI engine when needed, analyses the folder and
  shows the proposals. A finished analysis is repeated only when the folder gained images.
- New option "Analizuj nowe zdjęcia automatycznie" (off by default): new screenshots are
  named and sorted as soon as the folder is read.
- If the AI engine stops while the app runs, only analysis turns off; the catalog keeps
  working instead of asking for a restart.

The previous OCR package does not need to be downloaded again. The Qwen model is an
optional download of about 3.3 GB (4B) or 1.9 GB (2B).

New in 0.3.2: no repeated analysis, one language per name, a calmer look.

- Analyzing a folder again (for example after restarting the app) reuses the
  saved result of every image whose content and options did not change. Only
  new, changed or previously failed images go to the AI engine, so a folder
  that was already analyzed finishes in seconds instead of hours.
- Proposed names no longer mix Polish and English. "Do decyzji" has a PL / EN
  switch (Polish by default). Polish names translate the image description;
  words without a translation are left out. Text read from the image stays
  as written. Switching rewrites waiting proposals at once, without analyzing
  again; approved names are not changed.
- Calmer design: lime marks only the active view, progress and a small dot on
  images with a certain proposal. Tiles show just the picture and its name,
  and every card uses the same thin border.

The AI engine package is unchanged and does not need to be downloaded again.

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
