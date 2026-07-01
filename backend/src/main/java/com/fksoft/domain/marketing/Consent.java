package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Consent aggregate root (SPEC-0019 BR1): one <strong>immutable</strong> row in the append-only
 * consent log (DL-0056). A subject's decision (GRANTED/REVOKED) for a purpose, on a legal basis,
 * with the source and timestamp. The current state for a subject+purpose is the latest row
 * (resolved by the repository), so this entity is never updated after creation — revoking or
 * re-consenting writes a <em>new</em> row. Module-internal.
 *
 * <p>There is intentionally no {@code @Version}: rows are write-once (append-only), so optimistic
 * locking on an individual row is moot — concurrency is on the projection, which always reads the
 * latest row. The only mutation allowed is the LGPD erasure anonymizing the PII fields (DL-0058),
 * which is a privacy obligation, not a business state change.
 */
@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Consent {

  @Id private UUID id;

  private String subjectId;

  /** The subject-type cadastro code (was {@code SubjectType}; SPEC-0031/DL-0116). */
  private String subjectType;

  /** The purpose cadastro code (was {@code ConsentPurpose}; SPEC-0031/DL-0116). */
  private String purpose;

  @Enumerated(EnumType.STRING)
  private LegalBasis legalBasis;

  @Enumerated(EnumType.STRING)
  private ConsentStatus status;

  private String source;
  private Instant createdAt;
  private String createdBy;

  /**
   * Records a new consent decision as an immutable row (BR1/DL-0056).
   *
   * @param subject the subject (value)
   * @param purpose the purpose
   * @param legalBasis the legal basis
   * @param status the decision (GRANTED/REVOKED)
   * @param source the free-text source (audit), or {@code null}
   * @param now the instant the decision was recorded (UTC)
   * @param actor who recorded it (audit)
   * @return a new, persistable consent row
   */
  public static Consent record(
      SubjectRef subject,
      String purpose,
      LegalBasis legalBasis,
      ConsentStatus status,
      String source,
      Instant now,
      String actor) {
    Consent consent = new Consent();
    consent.id = UUID.randomUUID();
    consent.subjectId = subject.id();
    consent.subjectType = subject.type();
    consent.purpose = purpose;
    consent.legalBasis = legalBasis;
    consent.status = status;
    consent.source = blankToNull(source);
    consent.createdAt = now;
    consent.createdBy = actor;
    return consent;
  }

  /**
   * Anonymizes the PII fields of this row in place for an LGPD erasure (BR6/DL-0058): clears the
   * free-text {@code source} and replaces the subject id with an irreversible pseudonym, while
   * preserving the purpose, legal basis, status and timestamp so the revocation tombstone (and the
   * future-send suppression it backs) survives. Only ever called by the erasure use case.
   *
   * @param pseudonym the irreversible pseudonym to replace the subject id with
   */
  public void anonymize(String pseudonym) {
    this.subjectId = pseudonym;
    this.source = null;
    this.createdBy = "ERASED";
  }

  /** The row id. */
  public UUID id() {
    return id;
  }

  /** The current decision recorded by this row. */
  public ConsentStatus status() {
    return status;
  }

  /** The subject id (value). */
  public String subjectId() {
    return subjectId;
  }

  /** The subject-type cadastro code. */
  public String subjectType() {
    return subjectType;
  }

  /** The purpose cadastro code. */
  public String purpose() {
    return purpose;
  }

  /** The legal basis. */
  public LegalBasis legalBasis() {
    return legalBasis;
  }

  /** When the decision was recorded. */
  public Instant createdAt() {
    return createdAt;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Projects the row to its public read view. */
  public ConsentView toView() {
    return new ConsentView(
        id, subjectId, subjectType, purpose, legalBasis, status, source, createdAt);
  }
}
