package com.fksoft.infra.integration.payroll;

import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentTypeCodes;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.people.PeopleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Archives a collaborator processed payslip/espelho into the Compliance vault (SPEC-0022 BR5;
 * DL-0072). It is an <strong>orchestrator in {@code infra}</strong>, not a business module: it
 * routes the file to the existing Compliance vault ({@link ComplianceService#upload}) as a {@code
 * PAYROLL} document, which the vault stores with the legal 5-year retention (RetentionPolicy) and
 * the content hash. People never becomes a vault — it only keeps the returned {@code documentId} by
 * value (BR6).
 *
 * <p>The payslip carries personal data, so it is ingested with {@code hasPersonalData=true}: the
 * vault audits every access (LGPD — security.md). The orchestrator validates the employee exists
 * (via the {@link PeopleService} facade) before archiving, so a payslip is never archived for an
 * unknown collaborator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipArchivingService {

  private final ComplianceService complianceService;
  private final PeopleService peopleService;

  /**
   * Archives a payslip/espelho for a collaborator and period as a {@code PAYROLL} vault document
   * (5-year retention, personal data), returning the archived document.
   *
   * @param employeeId the collaborator id (must exist)
   * @param content the payslip bytes (non-empty)
   * @param originalFilename the original filename (validated by the vault; never trusted as the
   *     ref)
   * @param contentType the declared content type
   * @param issuedAt the issue date (drives the 5-year retention deadline)
   * @param period the period the payslip covers ({@code YYYY-MM})
   * @param actor who archives it (audit)
   * @return the archived vault document view (PAYROLL, with retentionUntil)
   */
  public DocumentView archive(
      java.util.UUID employeeId,
      byte[] content,
      String originalFilename,
      String contentType,
      java.time.LocalDate issuedAt,
      String period,
      String actor) {
    // Validate the collaborator exists (throws people.employee.not-found 404 otherwise).
    peopleService.getById(employeeId);
    DocumentView document =
        complianceService.upload(
            DocumentTypeCodes.PAYROLL,
            content,
            originalFilename,
            contentType,
            issuedAt,
            null,
            true, // payslip carries personal data (LGPD)
            null,
            null,
            actor);
    // Business event log — no PII (only ids/period).
    log.info(
        "PayslipArchived employeeId={} period={} documentId={} retentionUntil={}",
        employeeId,
        period,
        document.id(),
        document.retentionUntil());
    return document;
  }
}
