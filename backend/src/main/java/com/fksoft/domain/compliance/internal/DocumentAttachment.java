package com.fksoft.domain.compliance.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The link between a vault {@link Document} and a financial entry (SPEC-0008): the entry is
 * referenced by value ({@code entryId} + {@code entryType}) — no cross-module FK. The unique index
 * on {@code (document_id, entry_id)} makes the attach idempotent (BR5). Module-internal.
 */
@Entity
@Table(name = "document_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentAttachment {

  @Id private UUID id;

  private UUID documentId;

  private UUID entryId;

  private String entryType;

  private Instant attachedAt;

  private String attachedBy;

  /**
   * Creates the link between a document and a financial entry (by value).
   *
   * @param documentId the vault document id
   * @param entryId the financial entry id
   * @param entryType the entry's business type (value)
   * @param now attach instant (UTC)
   * @param actor who attached it (audit)
   * @return a new, persistable attachment
   */
  public static DocumentAttachment of(
      UUID documentId, UUID entryId, String entryType, Instant now, String actor) {
    DocumentAttachment attachment = new DocumentAttachment();
    attachment.id = UUID.randomUUID();
    attachment.documentId = documentId;
    attachment.entryId = entryId;
    attachment.entryType = entryType;
    attachment.attachedAt = now;
    attachment.attachedBy = actor;
    return attachment;
  }

  /** The attached document id. */
  public UUID documentId() {
    return documentId;
  }

  /** The entry id it was attached to. */
  public UUID entryId() {
    return entryId;
  }
}
