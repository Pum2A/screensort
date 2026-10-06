package pl.pum.screensort.scanning.api.dto;

import java.util.List;
import pl.pum.screensort.scanning.result.ScanResult;
import pl.pum.screensort.scanning.result.ScanSummary;

public record ScanResultDto(
    List<AcceptedImageDto> accepted, List<RejectedFileDto> rejected, ScanSummary summary) {
  public static ScanResultDto from(ScanResult source) {
    return new ScanResultDto(
        source.accepted().stream().map(AcceptedImageDto::from).toList(),
        source.rejected().stream().map(RejectedFileDto::from).toList(),
        source.summary());
  }
}
