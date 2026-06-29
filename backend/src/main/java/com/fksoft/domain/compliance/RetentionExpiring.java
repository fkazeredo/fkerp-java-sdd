package com.fksoft.domain.compliance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Business fact: a document is approaching its retention deadline (SPEC-0008 Events; redesign
 * 8.2-H, vault hygiene). Published in-process by the retention job; consumed by Intelligence.
 *
 * @param documentId the document id
 * @param retentionUntil the legal retention deadline
 * @param occurredAt when the job flagged it
 */
public record RetentionExpiring(UUID documentId, LocalDate retentionUntil, Instant occurredAt) {}
