package com.fksoft.infra.integration.pointclock;

import com.fksoft.domain.people.PointFailureClass;

/**
 * Raised by a {@link PointClockSource} when an outbound call to the vendor portal fails (SPEC-0012
 * BR2). It carries the {@link PointFailureClass} so the crawler can decide whether to retry or
 * dead-letter, and feeds observability. This is a technical (integration) exception confined to the
 * adapter — it never reaches the domain as a raw exception; the crawler classifies it and publishes
 * a domain {@code PointCrawlingFailed} event instead.
 */
public class PointClockSourceException extends RuntimeException {

  private final transient PointFailureClass failureClass;

  /**
   * @param failureClass the classification of the failure (TIMEOUT, UNAVAILABLE, ...)
   * @param message a non-sensitive description (never credentials — security.md)
   */
  public PointClockSourceException(PointFailureClass failureClass, String message) {
    super(message);
    this.failureClass = failureClass;
  }

  /** The failure classification driving retry/dead-letter and observability. */
  public PointFailureClass failureClass() {
    return failureClass;
  }
}
