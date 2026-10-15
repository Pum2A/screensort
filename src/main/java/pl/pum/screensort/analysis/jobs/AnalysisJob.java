package pl.pum.screensort.analysis.jobs;

import java.time.Instant;
import java.util.UUID;
import pl.pum.screensort.analysis.result.SavedAnalysis;

public record AnalysisJob(UUID id, long fileId, Status status, Instant startedAt,
    SavedAnalysis analysis, String errorMessage) {
  public enum Status { PREPARING, ANALYZING, SAVING, COMPLETED, FAILED }
}
