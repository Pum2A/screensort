package pl.pum.screensort.naming.api.dto;

import java.time.Instant;

/** A naming decision is separate from the unchanged filename in the catalog. */
public record NamingReviewDto(
    long fileId, long analysisId, String sourceSha256, String originalName,
    String proposedName, boolean proposalUncertain, boolean analysisMatchesCatalog, String approvedName,
    Instant approvedAt, boolean approvalMatchesCatalog, long revision) {}
