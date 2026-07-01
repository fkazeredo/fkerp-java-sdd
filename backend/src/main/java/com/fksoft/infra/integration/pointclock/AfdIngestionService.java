package com.fksoft.infra.integration.pointclock;

import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentTypeCodes;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.compliance.SignedFormatCodes;
import com.fksoft.domain.people.LegalTimeRecordArchived;
import com.fksoft.domain.people.PointAfdInvalidException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Ingestion of the signed legal time record AFD/AEJ (SPEC-0012 BR4; DL-0029/DL-0032). This is the
 * <strong>legal</strong> path of point integration — distinct from the operational snapshot: the
 * signed {@code .p7s} comes from the official export (REP-P export / REP-C USB upload — DL-0029),
 * its signature/integrity is verified ({@link AfdSignatureVerifier}), and it is stored in the
 * Compliance vault preserving the original signed file with {@code signedFormat=CAdES_P7S} and
 * 5-year retention (reusing SPEC-0008's {@code FileStorage} + {@code RetentionPolicy}). A {@code
 * LegalTimeRecordArchived} event is published.
 *
 * <p>Lives in {@code infra.integration} (the ACL), not in a business module: it orchestrates a
 * technical verification and routes the artifact to the existing Compliance vault. The AFD carries
 * personal data (CPF/PIS), so it is ingested with {@code hasPersonalData=true} (LGPD — access is
 * audited by the vault; security.md — never log content/credentials).
 */
@Slf4j
@Service
public class AfdIngestionService {

  private final AfdSignatureVerifier signatureVerifier;
  private final ComplianceService complianceService;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public AfdIngestionService(
      AfdSignatureVerifier signatureVerifier,
      ComplianceService complianceService,
      ApplicationEventPublisher events,
      Clock clock) {
    this.signatureVerifier = signatureVerifier;
    this.complianceService = complianceService;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Ingests a signed AFD/AEJ into the Compliance vault after verifying its signature/integrity
   * (BR4). Rejects a tampered/invalid artifact with {@link PointAfdInvalidException} (400) before
   * anything is stored.
   *
   * @param type the legal record type cadastro code ({@code TIME_RECORD_AFD} or {@code
   *     PROCESSED_JOURNAL_AEJ})
   * @param signedFile the signed {@code .p7s} bytes (the original, preserved as-is — BR4)
   * @param originalFilename the original filename (validated by the vault; never trusted as the
   *     ref)
   * @param expectedContentHash the declared SHA-256 of the signed content (tamper check)
   * @param issuedAt the issue date (drives the 5-year retention deadline)
   * @param periodRef the period the record covers ({@code YYYY-MM})
   * @param actor who ingests it (audit)
   * @return the archived vault document view (with {@code signedFormat=CAdES_P7S} and
   *     retentionUntil)
   * @throws PointAfdInvalidException when signature/integrity verification fails (BR4)
   */
  public DocumentView ingest(
      String type,
      byte[] signedFile,
      String originalFilename,
      String expectedContentHash,
      LocalDate issuedAt,
      String periodRef,
      String actor) {
    if (!DocumentTypeCodes.TIME_RECORD_AFD.equals(type)
        && !DocumentTypeCodes.PROCESSED_JOURNAL_AEJ.equals(type)) {
      // Only the two legal time-record types go through this path.
      throw new PointAfdInvalidException();
    }
    if (!signatureVerifier.verify(signedFile, expectedContentHash)) {
      // BR4: a tampered/invalid legal artifact must never enter the vault.
      throw new PointAfdInvalidException();
    }
    DocumentView document =
        complianceService.upload(
            type,
            signedFile,
            originalFilename,
            "application/pkcs7-signature",
            issuedAt,
            SignedFormatCodes.CAdES_P7S,
            true, // AFD/AEJ carries CPF/PIS — personal data (LGPD)
            null,
            null,
            actor);
    events.publishEvent(
        new LegalTimeRecordArchived(document.id(), type, periodRef, clock.instant()));
    log.info(
        "LegalTimeRecordArchived documentId={} type={} periodRef={} retentionUntil={}",
        document.id(),
        type,
        periodRef,
        document.retentionUntil());
    return document;
  }
}
