---
name: review-doc
description: Use when writing up a code review, audit or batch of fixes for the ScreenSort owner as a document in docs/.
---

# Review document

Write it to `docs/review-<topic>.md`, in Polish, following the existing files
(for example `docs/review-backend-quality.md`). Sections, in this order:

1. `# <Title> — co naprawiono i co zostało` plus two or three sentences of context.
2. `## Co naprawiono`: a table with columns area, problem, where (`file:line`), what changed.
3. `## Zmienione zachowanie widoczne dla użytkownika`: only what the user will actually notice.
4. `## Co zostało w backlogu`: each item with the reason it was not done now.
5. `## Luki poza <zakresem>`: gaps noticed outside the reviewed scope.
6. `## Weryfikacja`: the test names and the exact commands to run.

Rules:
- Every finding must be verified in the code before it is written down; subagent output is a lead.
- Order findings by severity (P0–P3). Tie each one to a concrete file and the failure it causes.
- Explain the impact in plain language (what the user feels), not just jargon.
- When the change ships, update the status line as described in the `desktop-release` skill.
