package pl.pum.screensort.analysis;

import java.time.Instant;

public record SavedAnalysis(long id, long fileId, Instant analyzedAt,
    boolean matchesCatalog, AnalysisResult result) {}
