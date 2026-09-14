# ScreenSort — Product Brief

Status: Draft v0.1  
Target: portfolio-quality MVP in 8–10 weeks  
Primary platform: Windows  
Product type: local single-user application

## 1. Problem

Screenshots are commonly stored under generated names such as `Screenshot_20260909_184212.png`. After the context in which they were created is forgotten, finding a specific screenshot becomes slow because the file name does not describe its contents.

Users need a safe way to understand, search and organize an existing screenshot folder without manually opening and renaming every image and without risking the loss of original files.

## 2. Primary user

The initial user is a Windows user who has hundreds or thousands of screenshots containing code, error messages, documents, purchases, conversations, educational material and entertainment.

The MVP is deliberately optimized for one local user and one explicitly selected source directory.

## 3. Job to be done

> When I remember what was visible on a screenshot but not its file name, I want to search for its content, receive a useful name and category suggestion, review the proposal and safely create an organized copy.

## 4. Primary workflow

1. The user selects an allowed source directory.
2. ScreenSort discovers supported image files inside that directory.
3. The application records file metadata and calculates SHA-256.
4. OCR extracts visible text.
5. Visual analysis produces content labels or a short description.
6. The application combines OCR, visual information and metadata to suggest:
   - a new file name;
   - a category;
   - tags;
   - a confidence score;
   - a short explanation of the suggestion.
7. The user sees a list or grid containing image previews and suggestions.
8. The user edits or accepts names and categories.
9. The user can search by original name, suggested name, OCR text, category and tags.
10. After explicit approval, ScreenSort creates verified copies in a separate output directory.
11. The application records the outcome of every copy operation.

## 5. MVP scope

### Source selection and discovery

- One explicitly selected source directory.
- PNG, JPG and JPEG files.
- Recursive scanning is configurable and disabled by default for the first technical slice.
- Manual rescan.
- File metadata: path, original name, MIME type, size and modification time.
- SHA-256 calculation.
- Duplicate detection by content hash.
- A repeated scan does not create duplicate catalog records.

### Analysis

- Local OCR for Polish and English text.
- Visual content analysis behind a replaceable analyzer interface.
- Initial visual labels such as code, document, product, conversation, meme and unknown.
- Suggested name, category, tags, confidence and explanation.
- A failed analyzer does not prevent the file from appearing in the review queue.

The visual analyzer may initially use a limited local model or deterministic signals. Training a custom model is not required for the MVP.

### Review interface

- Thumbnail preview.
- Original file name.
- Editable suggested file name.
- Editable category.
- OCR excerpt.
- Visual tags and confidence.
- Explanation of the suggestion.
- Individual and batch selection.
- Clear states for pending, analyzing, ready, failed, approved and copied files.

### Search

- Search by original and suggested file names.
- Search within OCR text.
- Filter by category, status and tags.
- Open or reveal the original file from the result.

### Safe copy

- A separate output directory.
- Copy only after explicit approval.
- Category-based output directories.
- No overwrite of existing files.
- Temporary `.partial` file.
- SHA-256 verification before finalization.
- Operation history and visible errors.
- Safe retry after failure.

## 6. Initial categories

1. Code and errors.
2. Documents and learning.
3. Products and shopping.
4. Invoices and payments.
5. Conversations.
6. Memes and entertainment.
7. Other / review required.

The category list is intentionally small. Category management is not part of the first technical slice.

## 7. Naming proposal

Suggested names should be readable, deterministic where possible and safe for Windows file systems.

Example:

```text
Original:  Screenshot_20260909_184212.png
Suggested: 2026-09-09_spring-nullpointerexception.png
Category:  Code and errors
```

The naming mechanism must:

- remove unsafe characters;
- enforce a maximum length;
- preserve the original extension;
- avoid reserved Windows names;
- resolve collisions without overwriting;
- allow manual editing before approval.

## 8. Success criteria

The MVP is successful when:

- it catalogs at least 500 demo screenshots without modifying the source directory;
- a second unchanged scan creates zero additional file records;
- users can find selected screenshots by text visible inside them;
- every reviewed file has an editable proposed name and category;
- at least 70% of category suggestions on a labeled demo set are accepted without correction;
- every completed copy has a checksum equal to its source;
- interruption during copying cannot damage the original;
- analysis and copy failures are visible and retryable;
- a new user can complete the primary workflow using the README and UI alone.

## 9. Explicitly out of scope

- Deleting source files.
- Moving source files.
- Scanning an entire drive or home directory.
- Running with administrator privileges.
- Automatic execution without review.
- Cloud-based analysis by default.
- Training a custom machine-learning model.
- PDF and office documents.
- Kafka and distributed workers.
- Multi-user accounts and authentication.
- Mobile applications.
- Cloud synchronization.

## 10. Product risks

| Risk | Consequence | Initial mitigation |
|---|---|---|
| Poor OCR quality | Search and naming become less useful | Language selection, preprocessing later, visible OCR result |
| Incorrect visual classification | Wrong category or name | Confidence, explanation and mandatory manual review |
| Unsafe path handling | Access outside the allowed directory | Canonical-path validation and no following of external links |
| Name collision | Existing output could be overwritten | No overwrite and deterministic suffix |
| Interrupted copy | Partial or ambiguous output | `.partial`, checksums, operation state and recovery |
| Excessive scope | No finished portfolio release | Strict MVP boundary and milestone exit criteria |
| Slow processing | Poor experience for large folders | Bounded parallelism, progress and later benchmarks |

## 11. Open decisions

- Exact local OCR integration.
- Exact local visual analysis implementation.
- Whether the first release uses PostgreSQL only or offers an embedded demo profile later.
- Default recursive scanning policy.

Open decisions must not block the first cataloging slice unless they directly affect it.
