package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of an operational point snapshot (SPEC-0012). It always reports {@code
 * operationalOnly = true} (BR3) so a consumer can never mistake it for the legal AFD/AEJ document
 * (that lives in the Compliance vault). The internal {@code payloadRef} is not exposed.
 *
 * @param id the snapshot id
 * @param sourceRef the REP/branch reference
 * @param periodRef the collected period ({@code YYYY-MM})
 * @param operationalOnly always {@code true} — this is operational data, not a retention document
 * @param marks the number of punches captured (operational metric)
 * @param collectedAt when the mirror was collected
 */
public record PointSnapshotView(
    UUID id,
    String sourceRef,
    String periodRef,
    boolean operationalOnly,
    int marks,
    Instant collectedAt) {}
