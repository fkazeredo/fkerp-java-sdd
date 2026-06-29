package com.fksoft.domain.compliance;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a document was attached to a financial entry (SPEC-0008 BR5). Published
 * in-process; the entry becomes conformant for the document the requirement demanded.
 *
 * @param documentId the attached document id
 * @param entryId the financial entry it was attached to
 * @param entryType the entry's business type (value)
 * @param occurredAt when the attach happened
 */
public record DocumentAttached(
    UUID documentId, UUID entryId, String entryType, Instant occurredAt) {}
