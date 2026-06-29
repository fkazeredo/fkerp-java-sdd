package com.fksoft.domain.intelligence;

/**
 * Lifecycle of the human decision on an insight (SPEC-0013 BR4). An insight is born {@code NEW};
 * the human accepts, rejects or dismisses it — and that decision is recorded (who/when) as the
 * "accepted × rejected" metric. The decision NEVER triggers an automatic action (BR2/BR3).
 */
public enum InsightStatus {
  NEW,
  ACCEPTED,
  REJECTED,
  DISMISSED
}
