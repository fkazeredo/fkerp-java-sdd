package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a signed legal time record (AFD or AEJ) was archived in the Compliance vault with
 * legal retention (SPEC-0012 BR4; Events). It is the legal counterpart of the operational {@link
 * PointSnapshotCollected} — published after the signed artifact is ingested into the vault. It
 * carries only the vault document id (by value, no cross-module FK), the type and the period.
 *
 * @param documentId the vault document id (in Compliance)
 * @param type the document-type cadastro code ({@code TIME_RECORD_AFD} or {@code
 *     PROCESSED_JOURNAL_AEJ})
 * @param periodRef the period the record covers ({@code YYYY-MM})
 * @param occurredAt when it was archived
 */
public record LegalTimeRecordArchived(
    UUID documentId, String type, String periodRef, Instant occurredAt) {}
