package com.fksoft.domain.marketing;

import java.time.Instant;

/**
 * The <strong>current</strong> consent state for a subject+purpose (SPEC-0019; DL-0056): the
 * projection of the most recent row in the append-only log. {@link #isGranted()} is the question
 * the send filter (BR2) asks before enqueuing a recipient.
 *
 * @param subject the subject (value)
 * @param purpose the purpose cadastro code
 * @param currentStatus the latest decision (GRANTED/REVOKED)
 * @param lastChangedAt when the latest decision was recorded
 */
public record ConsentState(
    SubjectRef subject, String purpose, ConsentStatus currentStatus, Instant lastChangedAt) {

  /** Whether the subject currently consents to the purpose (the send pre-condition, BR2). */
  public boolean isGranted() {
    return currentStatus == ConsentStatus.GRANTED;
  }
}
