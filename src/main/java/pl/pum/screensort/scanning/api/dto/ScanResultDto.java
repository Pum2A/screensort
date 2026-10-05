package pl.pum.screensort.scanning.api.dto;

import java.util.List;
import pl.pum.screensort.scanning.result.ScanResult;

public record ScanResultDto(List<AcceptedImageDto> accepted, List<RejectedFileDto> rejected) {
  public static ScanResultDto from(ScanResult source) {
    return new ScanResultDto(
        source.accepted().stream().map(AcceptedImageDto::from).toList(),
        source.rejected().stream().map(RejectedFileDto::from).toList());
  }
}
