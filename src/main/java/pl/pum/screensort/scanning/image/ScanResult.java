package pl.pum.screensort.scanning.image;

import java.util.List;
import java.util.Objects;
import pl.pum.screensort.scanning.safety.AllowedRoot;

public record ScanResult(
    AllowedRoot allowedRoot, List<AcceptedImage> accepted, List<RejectedFile> rejected) {
  public ScanResult {
    Objects.requireNonNull(allowedRoot);
    accepted = List.copyOf(accepted);
    rejected = List.copyOf(rejected);
  }
}
