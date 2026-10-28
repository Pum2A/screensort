package pl.pum.screensort.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisJob(UUID id, long fileId, Status status, Instant startedAt,
    SavedAnalysis analysis, String errorMessage) {
  public enum Status { PREPARING, ANALYZING, SAVING, COMPLETED, FAILED }
}
