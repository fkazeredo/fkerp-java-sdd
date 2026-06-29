package com.fksoft.domain.marketing;

/**
 * The status of a consent decision (SPEC-0019 BR1): a subject has either GRANTED a purpose or
 * REVOKED it. There is no mutable "current" column — each decision is an immutable row in the
 * append-only log and the current state is the latest row per {@code (subject, purpose)} (DL-0056).
 *
 * <p>{@code PENDING} is intentionally absent in the v1 single opt-in model (DL-0055); it would be
 * the additive seam if double opt-in is later required.
 */
public enum ConsentStatus {

  /** The subject granted consent for the purpose — sends are allowed (BR2). */
  GRANTED,

  /** The subject revoked consent for the purpose — sends are suppressed (BR2). */
  REVOKED
}
