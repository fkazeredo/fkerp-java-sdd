package com.fksoft.domain.billing;

import java.util.Set;

/**
 * Lifecycle of a commission invoice (SPEC-0016): {@code RASCUNHO → EMITIDA → CANCELADA}. Reissuing
 * is only possible by a new invoice after cancellation (BR4/BR6). Invalid transitions throw {@link
 * BillingInvoiceTransitionInvalidException}.
 */
public enum InvoiceStatus {
  RASCUNHO,
  EMITIDA,
  CANCELADA;

  private static final java.util.Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED =
      java.util.Map.of(
          RASCUNHO, Set.of(EMITIDA),
          EMITIDA, Set.of(CANCELADA),
          CANCELADA, Set.of());

  /** Whether this status may transition to {@code target}. */
  public boolean canTransitionTo(InvoiceStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }
}
