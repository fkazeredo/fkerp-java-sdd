package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an operational point snapshot was collected (SPEC-0012 BR2). Published in-process;
 * the HR journey processing (SPEC-0022) consumes it. It carries no legal weight — the snapshot is
 * operational only (BR3).
 *
 * @param snapshotId the collected snapshot id
 * @param sourceRef the REP/branch reference
 * @param periodRef the collected period ({@code YYYY-MM})
 * @param collectedAt when it was collected
 */
public record PointSnapshotCollected(
    UUID snapshotId, String sourceRef, String periodRef, Instant collectedAt) {}
