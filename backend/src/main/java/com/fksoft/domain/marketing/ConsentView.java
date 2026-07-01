package com.fksoft.domain.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a single consent log row (SPEC-0019). One row in the append-only history
 * (DL-0056). The {@code source} may be {@code null} after an LGPD erasure anonymized it (DL-0058).
 *
 * @param id the row id
 * @param subjectId the subject id (value)
 * @param subjectType the subject-type cadastro code
 * @param purpose the purpose cadastro code
 * @param legalBasis the legal basis
 * @param status the decision (GRANTED/REVOKED)
 * @param source the free-text source, or {@code null}
 * @param createdAt when the decision was recorded
 */
public record ConsentView(
    UUID id,
    String subjectId,
    String subjectType,
    String purpose,
    LegalBasis legalBasis,
    ConsentStatus status,
    String source,
    Instant createdAt) {}
