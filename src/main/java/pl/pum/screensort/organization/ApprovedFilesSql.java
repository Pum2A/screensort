package pl.pum.screensort.organization;

/** Shared eligibility rules for the preview and the export snapshot. */
public final class ApprovedFilesSql {
  private ApprovedFilesSql() {}

  public static final String FROM = """
      from analysis_batch_item i
      join file_record f on f.id = i.file_id
      join allowed_root u on u.id = f.allowed_root_id
      join file_analysis a on a.id = i.analysis_id
      join file_organization_review r on r.file_record_id = f.id
      join file_naming n on n.file_record_id = f.id
      where i.batch_id = ? and i.status = 'COMPLETED'
        and i.analysis_id = r.analysis_id
        and i.source_sha256 = f.sha256 and a.source_sha256 = f.sha256
        and r.decision = 'APPROVED' and r.source_sha256 = f.sha256
        and n.source_sha256 = f.sha256 and n.analysis_id = r.analysis_id
        and n.revision = r.naming_revision
      """;
}
