package com.fksoft.domain.compliance;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Document aggregate in the vault (SPEC-0008): a supporting document with its content hash, signed
 * format and the legal retention deadline computed at ingestion (BR2). Signed artifacts preserve
 * the original signed file (BR3 — never regenerated). Purge is rejected before the retention
 * deadline (BR7), an aggregate invariant. Module-internal.
 */
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Document {

  @Id private UUID id;

  /** The document-type cadastro code (was {@code DocumentType}; SPEC-0031/DL-0117). */
  private String type;

  private String fileRef;

  private String hash;

  private LocalDate issuedAt;

  private LocalDate retentionUntil;

  /** The signed-format cadastro code (was {@code SignedFormat}; SPEC-0031/DL-0117), or null. */
  private String signedFormat;

  private boolean hasPersonalData;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Ingests a document, computing {@code retentionUntil} from the type and issue date (BR2). The
   * signed format (if any) is recorded; the original signed file is the one stored (BR3).
   *
   * @param type the document-type cadastro code
   * @param fileRef the opaque storage reference
   * @param hash the content hash ({@code sha256:...})
   * @param issuedAt the issue date
   * @param signedFormat the signed-format cadastro code, or {@code null}
   * @param hasPersonalData whether it carries personal data (BR8)
   * @param now ingestion instant (UTC)
   * @param actor who ingested it (audit)
   * @return a new, persistable document
   */
  public static Document ingest(
      String type,
      String fileRef,
      String hash,
      LocalDate issuedAt,
      String signedFormat,
      boolean hasPersonalData,
      Instant now,
      String actor) {
    Document document = new Document();
    document.id = UUID.randomUUID();
    document.type = type;
    document.fileRef = fileRef;
    document.hash = hash;
    document.issuedAt = issuedAt;
    document.retentionUntil = RetentionPolicy.retentionUntil(type, issuedAt);
    document.signedFormat = signedFormat;
    document.hasPersonalData = hasPersonalData;
    document.createdAt = now;
    document.updatedAt = now;
    document.createdBy = actor;
    document.updatedBy = actor;
    return document;
  }

  /**
   * Ensures the document may be purged: rejects the purge while {@code now} is before the retention
   * deadline (BR7).
   *
   * @param today the current date
   * @throws ComplianceRetentionNotExpiredException when still within retention (BR7)
   */
  public void ensurePurgeable(LocalDate today) {
    if (today.isBefore(retentionUntil)) {
      throw new ComplianceRetentionNotExpiredException();
    }
  }

  /** The document id. */
  public UUID id() {
    return id;
  }

  /** The opaque storage reference (module-internal; never exposed in views). */
  public String fileRef() {
    return fileRef;
  }

  /** Projects the aggregate to its public read view (without the internal {@code fileRef}). */
  public DocumentView toView() {
    return new DocumentView(
        id, type, hash, issuedAt, retentionUntil, signedFormat, hasPersonalData, createdAt);
  }
}
