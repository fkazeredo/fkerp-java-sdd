package com.fksoft.domain.marketing;

import java.time.Instant;

/**
 * Business fact: a subject revoked consent for a purpose (SPEC-0019 Events). Published in-process;
 * a future consumer uses it for suppression. Carries the subject <strong>reference</strong> (value)
 * and the purpose — no extra PII (LGPD: events do not leak personal data beyond the natural key).
 *
 * @param subjectRef the subject id (value)
 * @param subjectType the subject kind
 * @param purpose the purpose revoked
 * @param occurredAt when the revocation was recorded
 */
public record ConsentRevoked(
    String subjectRef, SubjectType subjectType, ConsentPurpose purpose, Instant occurredAt) {}
