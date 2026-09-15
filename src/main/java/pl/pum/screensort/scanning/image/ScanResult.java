package pl.pum.screensort.scanning.image;

import java.util.List;

public record ScanResult(List<ScannedImage> accepted,List<RejectedFile> rejected) {
    public ScanResult{
        accepted = List.copyOf(accepted);
        rejected = List.copyOf(rejected);
    }
}
