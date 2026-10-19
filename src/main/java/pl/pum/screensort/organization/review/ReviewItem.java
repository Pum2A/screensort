package pl.pum.screensort.organization.review;

import java.time.Instant;

/** A batch suggestion beside the current user decision; neither is a filesystem operation. */
public record ReviewItem(
    long fileId, String originalName, String status, String category, String proposedName,
    String message, Long analysisId, String sourceSha256, boolean analysisMatchesCatalog,
    long namingRevision, String approvedName, Decision review) {
  public record Decision(String decision, String category, Instant reviewedAt, long revision,
      boolean matchesCatalog) {}
}
