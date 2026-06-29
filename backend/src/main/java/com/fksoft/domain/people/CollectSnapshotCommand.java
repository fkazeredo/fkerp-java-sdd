package com.fksoft.domain.people;

/**
 * Domain command to collect an operational point snapshot (SPEC-0012). This is the
 * <strong>translated</strong> shape that crosses the ACL boundary from the crawler adapter (the
 * external portal mirror DTO stays in {@code infra.integration.pointclock} — BR6/DL-0030). It
 * carries only domain values: the source/period (the idempotency key, BR5), the opaque payload
 * reference of the captured mirror, and how many punches the mirror held.
 *
 * @param sourceRef the REP/branch reference (e.g. {@code REP-FILIAL-SP})
 * @param periodRef the collected period ({@code YYYY-MM})
 * @param payloadRef the opaque reference to the stored mirror (operational payload, via
 *     FileStorage)
 * @param marks the number of punches captured in the mirror (operational metric)
 */
public record CollectSnapshotCommand(
    String sourceRef, String periodRef, String payloadRef, int marks) {}
