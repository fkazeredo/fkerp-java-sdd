package com.fksoft.domain.marketing;

/**
 * The outcome of an LGPD erasure request (SPEC-0019 BR6; DL-0058): how many consent rows were
 * anonymized and whether the subject ends up suppressed (a revocation tombstone preserved so the
 * subject is never silently re-included in a future send). No data that another legal basis
 * requires to keep (fiscal in Compliance, etc.) is touched.
 *
 * @param subjectId the (now pseudonymized) subject reference
 * @param anonymizedConsents how many consent rows were anonymized
 * @param suppressed whether a revocation tombstone now suppresses the subject
 */
public record ErasureResult(String subjectId, int anonymizedConsents, boolean suppressed) {}
