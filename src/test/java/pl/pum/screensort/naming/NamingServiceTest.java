package pl.pum.screensort.naming;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.pum.screensort.analysis.database.AnalysisStore;
import pl.pum.screensort.analysis.result.AnalysisResult;
import pl.pum.screensort.analysis.result.SavedAnalysis;
import pl.pum.screensort.catalog.database.FileRecordEntity;
import pl.pum.screensort.catalog.database.FileRecordRepository;
import pl.pum.screensort.naming.database.FileNamingEntity;
import pl.pum.screensort.naming.database.FileNamingRepository;

class NamingServiceTest {
  private static final String HASH = "a".repeat(64);
  private final FileRecordRepository files = mock(FileRecordRepository.class);
  private final AnalysisStore analyses = mock(AnalysisStore.class);
  private final FileNamingRepository names = mock(FileNamingRepository.class);
  private final FileRecordEntity file = mock(FileRecordEntity.class);
  private final NamingService service = new NamingService(files, analyses, names, new NameSuggestionGenerator(() -> NamingLanguage.EN));

  @BeforeEach
  void prepare() {
    when(file.getId()).thenReturn(1L);
    when(file.getOriginalName()).thenReturn("IMG_0026.png");
    when(file.getSha256()).thenReturn(HASH);
    when(files.findById(1L)).thenReturn(Optional.of(file));
    when(files.findForNamingById(1L)).thenReturn(Optional.of(file));
    when(analyses.latest(1)).thenReturn(Optional.of(analysis(HASH, true)));
    when(names.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void readingUsesSavedDescriptionAndDoesNotWrite() {
    var result = service.read(1);
    assertThat(result.proposedName()).isEqualTo("mug_desk.png");
    assertThat(result.originalName()).isEqualTo("IMG_0026.png");
    assertThat(result.revision()).isEqualTo(-1);
    assertThat(result.approvedName()).isNull();
    verify(names, never()).saveAndFlush(any());
  }

  @Test
  void approvingSavesTheUserNameNotTheGeneratedNameOrCatalogName() {
    var result = service.approve(1, 8, HASH, -1, "moj_kubek.png");
    assertThat(result.approvedName()).isEqualTo("moj_kubek.png");
    assertThat(result.approvalMatchesCatalog()).isTrue();
    assertThat(result.approvedAt()).isNotNull();
    verify(names).saveAndFlush(any());
    verify(file, never()).updateFromScan(any(), any(), anyLong(), any(), any(), any());
  }

  @Test
  void rejectsStaleAnalysisAndStaleOpenFormsBeforeWriting() {
    when(file.getSha256()).thenReturn("b".repeat(64));
    assertFailure(() -> service.approve(1, 8, HASH, -1, "mug.png"), "ANALYSIS_OUTDATED");
    when(file.getSha256()).thenReturn(HASH);
    assertFailure(() -> service.approve(1, 9, HASH, -1, "mug.png"), "ANALYSIS_CHANGED");
    assertFailure(() -> service.approve(1, 8, "b".repeat(64), -1, "mug.png"), "ANALYSIS_CHANGED");
    var decision = new FileNamingEntity(file);
    decision.approve(8, HASH, "poprzednia.png", Instant.now());
    when(names.findByFile_Id(1)).thenReturn(Optional.of(decision));
    assertFailure(() -> service.approve(1, 8, HASH, -1, "mug.png"), "REVIEW_CHANGED");
    verify(names, never()).saveAndFlush(any());
  }

  @Test
  void keepsOldDecisionVisibleButDoesNotTreatItAsValidForChangedContent() {
    var decision = new FileNamingEntity(file);
    decision.approve(8, "b".repeat(64), "stary_plik.png", Instant.now());
    when(names.findByFile_Id(1)).thenReturn(Optional.of(decision));
    var result = service.read(1);
    assertThat(result.approvedName()).isEqualTo("stary_plik.png");
    assertThat(result.approvalMatchesCatalog()).isFalse();
    assertThat(result.proposedName()).isEqualTo("mug_desk.png");
  }

  @Test
  void acceptsManualNamesWhenVisualAnalysisWasSkipped() {
    when(analyses.latest(1)).thenReturn(Optional.of(analysis(HASH, false)));
    assertThat(service.read(1).proposedName()).isNull();
    assertThat(service.approve(1, 8, HASH, -1, "wlasna_nazwa.png").approvedName()).isEqualTo("wlasna_nazwa.png");
  }

  @Test
  void combinesTextOnTheImageWithTheCaptionAndFlagsOcrOnlyGuesses() {
    var ocr = new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED,
        new AnalysisResult.Ocr("ROBLOX\nLobby", "RapidOCR", "test", 1), null);
    var both = new AnalysisResult(HASH, ocr, analysis(HASH, true).result().visual());
    when(analyses.latest(1)).thenReturn(Optional.of(new SavedAnalysis(8, 1, Instant.now(), true, both)));
    assertThat(service.read(1).proposedName()).isEqualTo("roblox_lobby_mug_desk.png");
    assertThat(service.read(1).proposalUncertain()).isFalse();

    var onlyText = new AnalysisResult(HASH, new AnalysisResult.OcrStep(AnalysisResult.Status.COMPLETED,
        new AnalysisResult.Ocr("ROBLOX", "RapidOCR", "test", 1), null),
        new AnalysisResult.VisualStep(AnalysisResult.Status.SKIPPED, null, null));
    when(analyses.latest(1)).thenReturn(Optional.of(new SavedAnalysis(8, 1, Instant.now(), true, onlyText)));
    assertThat(service.read(1).proposedName()).isEqualTo("roblox.png");
    assertThat(service.read(1).proposalUncertain()).isTrue();
  }

  @Test
  void rejectsInvalidNamesMissingAnalysisAndInvalidRequests() {
    assertFailure(() -> service.approve(1, 8, HASH, -1, "../mug.png"), "INVALID_NAME");
    assertFailure(() -> service.approve(1, 8, HASH, -1, null), "INVALID_NAME");
    assertFailure(() -> service.approve(1, 8, "bad-hash", -1, "mug.png"), "INVALID_REQUEST");
    assertFailure(() -> service.read(0), "INVALID_REQUEST");
    assertFailure(() -> service.read(55), "FILE_NOT_FOUND");
    when(analyses.latest(1)).thenReturn(Optional.empty());
    assertFailure(() -> service.read(1), "ANALYSIS_REQUIRED");
    verify(names, never()).saveAndFlush(any());
  }

  private static void assertFailure(ThrowingCallable action, String code) {
    assertThatThrownBy(action).isInstanceOfSatisfying(NamingException.class,
        error -> assertThat(error.code()).isEqualTo(code));
  }

  static SavedAnalysis analysis(String hash, boolean visual) {
    var result = new AnalysisResult(hash,
        new AnalysisResult.OcrStep(AnalysisResult.Status.SKIPPED, null, null),
        visual ? new AnalysisResult.VisualStep(AnalysisResult.Status.COMPLETED,
            new AnalysisResult.Visual("A mug on a desk.", "test-only", "test-revision", "caption", "cpu", 1), null)
            : new AnalysisResult.VisualStep(AnalysisResult.Status.SKIPPED, null, null));
    return new SavedAnalysis(8, 1, Instant.parse("2026-10-18T12:00:00Z"), true, result);
  }
}
