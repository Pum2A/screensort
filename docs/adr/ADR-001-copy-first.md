# ADR-001: Read-only source and copy-first organization

Date: 2026-09-09  
Status: Accepted

## Context

ScreenSort processes personal screenshots that may be unique, private or impossible to recreate. Classification and naming suggestions can be incorrect, the application may crash during an operation, and the filesystem cannot participate in the same transaction as the PostgreSQL database.

Automatically renaming, moving or deleting source files in the first release would create unnecessary risk and would make early implementation errors potentially destructive.

## Decision

The MVP treats the selected source directory as read-only.

ScreenSort may:

- discover files;
- read metadata and content;
- calculate checksums;
- create previews and analysis results;
- store catalog and review information in its database;
- create a copy in an explicitly selected output directory after user approval.

ScreenSort must not:

- rename source files;
- modify source contents;
- delete source files;
- overwrite existing output files;
- follow paths outside the allowed source root.

Every approved copy is written to a temporary `.partial` target, verified against the source SHA-256 checksum and only then finalized. If any safety check fails, the operation stops and records an error.

## Alternatives considered

### Rename files in place

Rejected for the MVP because incorrect suggestions or failures could disrupt the user's existing organization.

### Move files directly to categorized folders

Rejected for the MVP because a partially completed move is harder to recover and may remove the only original copy.

### Copy files without verification

Rejected because a successful filesystem call alone does not provide sufficient evidence of content integrity for this product's safety promise.

### Index files without any organization operation

Viable as the first technical slice, but insufficient for the complete MVP because the product should create useful, organized output after review.

## Consequences

### Positive

- Original data remains recoverable.
- Manual review can be introduced before any write operation.
- Failure testing is safer.
- The product has a clear privacy and safety story.
- Copy operations can be made idempotent and auditable.

### Negative

- The application requires additional disk space.
- Users may temporarily have two copies of a file.
- True cleanup remains a manual action outside the application.
- Copy verification and collision handling add implementation work.

## Future reconsideration

Safe moving may be considered only after the copy workflow and recovery process are stable and tested. It must require explicit opt-in, verified backup and a separate ADR.

