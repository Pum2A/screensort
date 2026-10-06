package pl.pum.screensort.scanning.result;

import java.util.List;
import java.util.Objects;
import pl.pum.screensort.scanning.folder.AllowedRoot;

public record ScanResult(
    AllowedRoot allowedRoot,
    List<AcceptedImage> accepted,
    List<RejectedFile> rejected,
    ScanSummary summary) {
  public ScanResult {
    Objects.requireNonNull(allowedRoot);
    accepted = List.copyOf(accepted);
    rejected = List.copyOf(rejected);
    Objects.requireNonNull(summary);
    long acceptedCount =
        (long) summary.newImages() + summary.rescannedImages() + summary.unchangedImages();
    if (acceptedCount != accepted.size() || summary.rejectedFiles() != rejected.size()) {
      throw new IllegalArgumentException("Podsumowanie musi odpowiadać listom wyników skanu.");
    }
  }
}
