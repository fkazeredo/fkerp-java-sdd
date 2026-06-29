package com.fksoft.domain.exchange;

/**
 * Lifecycle of an {@link com.fksoft.domain.exchange.internal.FxPosition} (SPEC-0011): {@link #OPEN}
 * from the confirmed sale until the supplier settlement is recorded, then {@link #CLOSED} with the
 * realized drift and total gap.
 */
public enum FxPositionStatus {
  OPEN,
  CLOSED
}
