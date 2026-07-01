package com.fksoft.domain.compliance;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.finance.LedgerDirectory;
import com.fksoft.domain.finance.LedgerEntrySnapshot;
import com.fksoft.domain.finance.PendingEntry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Compliance module (SPEC-0008): ingests documents into the vault
 * (computing the content hash and retention deadline), attaches them to financial entries (by
 * value), answers the period close-check (the veto consumed by Finance), reads content (audited)
 * and blocks purge before retention. It reads ledger entries only through the Finance {@link
 * LedgerDirectory} port (no cross-module FK).
 *
 * <p>It also implements the {@link DocumentRequirementDirectory} read port (SPEC-0025; DL-0086), so
 * the Admin module can learn which documents an entry type requires at registration — referencing
 * the requirement without imposing it (the veto stays here).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService implements DocumentRequirementDirectory {

  private final DocumentRepository documents;
  private final DocumentAttachmentRepository attachments;
  private final DocumentRequirementRepository requirements;
  private final FileStorage fileStorage;
  private final LedgerDirectory ledgerDirectory;
  private final CadastroValidator cadastroValidator;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Ingests a document: validates the upload, stores the binary, computes the SHA-256 content hash
   * and the retention deadline (BR1/BR2/DL-0015), and optionally attaches it to a financial entry.
   *
   * @param type the document-type cadastro code
   * @param content the document bytes (non-empty)
   * @param originalFilename the original filename (for validation; never trusted as the ref)
   * @param contentType the declared content type
   * @param issuedAt the issue date
   * @param signedFormat the signed-format cadastro code, or {@code null}
   * @param hasPersonalData whether it carries personal data (BR8)
   * @param entryId the financial entry to attach to, or {@code null}
   * @param entryType the entry's business type (value), required when {@code entryId} is set
   * @param actor who ingests it (audit)
   * @return the ingested document view
   * @throws ComplianceUploadInvalidException when the upload is invalid (BR / validation)
   */
  @Transactional
  public DocumentView upload(
      String type,
      byte[] content,
      String originalFilename,
      String contentType,
      LocalDate issuedAt,
      String signedFormat,
      boolean hasPersonalData,
      UUID entryId,
      String entryType,
      String actor) {
    if (type == null
        || type.isBlank()
        || content == null
        || content.length == 0
        || issuedAt == null) {
      throw new ComplianceUploadInvalidException();
    }
    // Validate the document-type reference code against the cadastro (SPEC-0031 BR3/DL-0117) — an
    // unknown/inactive type is rejected (422) before anything is stored. The signed format is NOT
    // validated on write: it is produced by the ingesting adapter (XADES for the NFS-e, CAdES_P7S
    // for the AFD/AEJ), never a free-form user payload (DL-0117, like the system-produced
    // MarketRateSource) — and its wired value CAdES_P7S is deliberately mixed-case, which the
    // upper-casing validator would not match. It remains a cadastro so the label is editable.
    cadastroValidator.validate(CadastroType.DOCUMENT_TYPE, type);
    String fileRef = fileStorage.store(content, originalFilename, contentType);
    String hash = sha256(content);
    Instant now = clock.instant();
    Document document =
        Document.ingest(type, fileRef, hash, issuedAt, signedFormat, hasPersonalData, now, actor);
    documents.save(document);
    log.info(
        "DocumentIngested documentId={} type={} retentionUntil={} personalData={}",
        document.id(),
        type,
        document.retentionUntil(),
        hasPersonalData);
    if (entryId != null) {
      attach(document.id(), entryId, entryType, actor);
    }
    return document.toView();
  }

  /**
   * Attaches a document to a financial entry (BR5), idempotently (BR5: unique {@code (document_id,
   * entry_id)}). Publishes {@code DocumentAttached}.
   *
   * @param documentId the vault document id
   * @param entryId the financial entry id
   * @param entryType the entry's business type (value)
   * @param actor who attaches it (audit)
   * @throws ComplianceDocumentNotFoundException when the document does not exist
   */
  @Transactional
  public void attach(UUID documentId, UUID entryId, String entryType, String actor) {
    if (!documents.existsById(documentId)) {
      throw new ComplianceDocumentNotFoundException();
    }
    if (attachments.existsByDocumentIdAndEntryId(documentId, entryId)) {
      return;
    }
    Instant now = clock.instant();
    try {
      attachments.save(DocumentAttachment.of(documentId, entryId, entryType, now, actor));
    } catch (DataIntegrityViolationException alreadyAttached) {
      log.info("Document {} already attached to entry {}", documentId, entryId);
      return;
    }
    events.publishEvent(new DocumentAttached(documentId, entryId, entryType, now));
    log.info(
        "DocumentAttached documentId={} entryId={} entryType={}", documentId, entryId, entryType);
  }

  /**
   * Fetches a document's metadata.
   *
   * @throws ComplianceDocumentNotFoundException when the document does not exist
   */
  @Transactional(readOnly = true)
  public DocumentView getById(UUID documentId) {
    return documents
        .findById(documentId)
        .map(Document::toView)
        .orElseThrow(ComplianceDocumentNotFoundException::new);
  }

  /**
   * Reads a document's content (download). Access is audited (BR8 — LGPD), without leaking content.
   *
   * @throws ComplianceDocumentNotFoundException when the document does not exist
   */
  @Transactional(readOnly = true)
  public byte[] readContent(UUID documentId, String actor) {
    Document document =
        documents.findById(documentId).orElseThrow(ComplianceDocumentNotFoundException::new);
    log.info(
        "DocumentAccessed documentId={} personalData={} accessedBy={}",
        documentId,
        document.hasPersonalData(),
        actor);
    return fileStorage.read(document.fileRef());
  }

  /**
   * Runs the close-check for a period (BR6): a period may close when every entry has the documents
   * its type requires at registration (DL-0012). Returns the verdict and the pending entries;
   * publishes {@code RequirementUnmet} when it cannot close.
   *
   * @param period the period to check ({@code YYYY-MM})
   * @return the close-check verdict
   */
  @Transactional(readOnly = true)
  public CloseCheckView closeCheck(String period) {
    List<LedgerEntrySnapshot> entries = ledgerDirectory.entriesOfPeriod(period);
    List<UUID> entryIds = entries.stream().map(LedgerEntrySnapshot::entryId).toList();
    Map<UUID, List<DocumentAttachment>> attachmentsByEntry =
        entryIds.isEmpty()
            ? Map.of()
            : attachments.findByEntryIdIn(entryIds).stream()
                .collect(Collectors.groupingBy(DocumentAttachment::entryId));

    List<PendingEntry> pending = new ArrayList<>();
    for (LedgerEntrySnapshot entry : entries) {
      Set<String> required = requiredDocumentTypes(entry.entryType());
      if (required.isEmpty()) {
        continue;
      }
      Set<String> satisfied = attachedDocumentTypes(attachmentsByEntry.get(entry.entryId()));
      List<String> missing = required.stream().filter(doc -> !satisfied.contains(doc)).toList();
      if (!missing.isEmpty()) {
        pending.add(new PendingEntry(entry.entryId(), entry.entryType(), missing));
      }
    }

    boolean canClose = pending.isEmpty();
    if (!canClose) {
      events.publishEvent(new RequirementUnmet(period, pending, clock.instant()));
      log.info("CloseCheckBlocked period={} pending={}", period, pending.size());
    }
    return new CloseCheckView(period, canClose, pending);
  }

  /**
   * Purges a document (BR7): rejected while within retention. Removes the binary and the metadata.
   *
   * @param documentId the document id
   * @param actor who purges it (audit)
   * @throws ComplianceDocumentNotFoundException when the document does not exist
   * @throws ComplianceRetentionNotExpiredException when still within retention (BR7)
   */
  @Transactional
  public void purge(UUID documentId, String actor) {
    Document document =
        documents.findById(documentId).orElseThrow(ComplianceDocumentNotFoundException::new);
    document.ensurePurgeable(LocalDate.now(clock));
    fileStorage.delete(document.fileRef());
    documents.delete(document);
    log.info("DocumentPurged documentId={} purgedBy={}", documentId, actor);
  }

  /**
   * Flags documents whose retention deadline is within {@code horizonDays}, publishing {@code
   * RetentionExpiring} for each (idempotent: re-runs publish again, harmlessly — the job is a
   * read).
   *
   * @param horizonDays how many days ahead to look
   * @return how many documents were flagged
   */
  @Transactional(readOnly = true)
  public int flagRetentionExpiring(int horizonDays) {
    LocalDate cutoff = LocalDate.now(clock).plusDays(horizonDays);
    List<Document> expiring = documents.findByRetentionUntilLessThanEqual(cutoff);
    Instant now = clock.instant();
    for (Document document : expiring) {
      events.publishEvent(new RetentionExpiring(document.id(), document.retentionUntil(), now));
    }
    if (!expiring.isEmpty()) {
      log.info(
          "RetentionExpiring flagged {} document(s) within {} days", expiring.size(), horizonDays);
    }
    return expiring.size();
  }

  /**
   * The document types required at registration for an entry type (DL-0012), as the public {@link
   * DocumentRequirementDirectory} port consumed by the Admin module (DL-0086). Read-only — it
   * surfaces the requirement, it never imposes it.
   *
   * @param entryType the Finance entry type name (value)
   * @return the required document type names (empty when none)
   */
  @Override
  @Transactional(readOnly = true)
  public List<String> requiredAtRegistration(String entryType) {
    return List.copyOf(requiredDocumentTypes(entryType));
  }

  private Set<String> requiredDocumentTypes(String entryType) {
    Set<String> required = new LinkedHashSet<>();
    for (DocumentRequirement requirement :
        requirements.findByEntryTypeAndPhase(entryType, RequirementPhaseCodes.AT_REGISTRATION)) {
      required.add(requirement.requiredDocumentType());
    }
    return required;
  }

  private Set<String> attachedDocumentTypes(List<DocumentAttachment> entryAttachments) {
    if (entryAttachments == null || entryAttachments.isEmpty()) {
      return Set.of();
    }
    List<UUID> documentIds = entryAttachments.stream().map(DocumentAttachment::documentId).toList();
    Set<String> types = new LinkedHashSet<>();
    for (Document document : documents.findAllById(documentIds)) {
      types.add(document.type());
    }
    return types;
  }

  private static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "sha256:" + HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
