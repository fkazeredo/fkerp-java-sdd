package com.fksoft.domain.people;

/**
 * Classification of a point-crawl failure (SPEC-0012 BR2; {@code messaging-and-integrations.md}).
 * The class decides whether the failure is retryable and feeds observability ({@code
 * point_crawl_failures_total{class}}). A failure is always classified and never produces a
 * misleading business result (no fake snapshot). External (metric/log) value is the constant name.
 */
public enum PointFailureClass {

  /** The portal call exceeded its timeout. Retryable. */
  TIMEOUT(true),

  /** The portal was unreachable/unavailable. Retryable. */
  UNAVAILABLE(true),

  /** Authentication against the portal failed. <strong>Not</strong> retryable (retrying loops). */
  AUTHENTICATION_FAILED(false),

  /** The portal answered with an unparseable/invalid mirror. Not retryable (the shape is wrong). */
  INVALID_RESPONSE(false),

  /** Any other, unclassified failure. Not retryable by default. */
  UNKNOWN_ERROR(false);

  private final boolean retryable;

  PointFailureClass(boolean retryable) {
    this.retryable = retryable;
  }

  /**
   * Whether a failure of this class is worth retrying (BR2; {@code messaging-and-integrations.md}).
   */
  public boolean retryable() {
    return retryable;
  }
}
