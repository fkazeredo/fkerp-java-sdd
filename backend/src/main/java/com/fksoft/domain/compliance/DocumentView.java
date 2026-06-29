package com.fksoft.domain.compliance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of a vault document (SPEC-0008): its type, hash, issue date, computed retention
 * deadline and optional signed format. The {@code fileRef} is intentionally <strong>not</strong>
 * exposed (it is an internal opaque storage handle).
 *
 * @param id the document id
 * @param type the document type
 * @param hash the content hash ({@code sha256:...})
 * @param issuedAt the issue date
 * @param retentionUntil the legal retention deadline (BR2)
 * @param signedFormat the signed format, or {@code null}
 * @param hasPersonalData whether it carries personal data (LGPD-controlled access, BR8)
 * @param createdAt when it was ingested
 */
public record DocumentView(
    UUID id,
    DocumentType type,
    String hash,
    LocalDate issuedAt,
    LocalDate retentionUntil,
    SignedFormat signedFormat,
    boolean hasPersonalData,
    Instant createdAt) {}
