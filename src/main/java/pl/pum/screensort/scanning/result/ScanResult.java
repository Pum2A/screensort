package pl.pum.screensort.scanning.result;

import java.util.List;
import java.util.Objects;
import pl.pum.screensort.scanning.folder.AllowedRoot;

public record ScanResult(
    AllowedRoot allowedRoot, List<AcceptedImage> accepted, List<RejectedFile> rejected) {
  public ScanResult {
    Objects.requireNonNull(allowedRoot);
    accepted = List.copyOf(accepted);
    rejected = List.copyOf(rejected);
  }
}
