package com.fksoft.domain.intelligence;

/**
 * The axis an insight is about (SPEC-0013 BR1/BR8, DL-0034). The v1 {@code PromoFxAdvisor} emits
 * {@code AGENCY} — the only axis derivable from the events the module consumes ({@code
 * BookingConfirmed.accountId}). {@code ROUTE}/{@code PRODUCT}/{@code SUPPLIER} are designed in so
 * they can be plugged later (when a producer event carries them) without reshaping the aggregate or
 * the {@code insights} table.
 */
public enum SubjectKind {
  AGENCY,
  ROUTE,
  PRODUCT,
  SUPPLIER
}
