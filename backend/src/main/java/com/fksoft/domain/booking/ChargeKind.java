package com.fksoft.domain.booking;

/**
 * The kind of obligation produced by a cancellation or no-show (SPEC-0010 BR5/BR6). These are
 * distinct facts that NEVER net out against each other (BR11/DL-0024). External value is the
 * constant name.
 *
 * <ul>
 *   <li>{@link #PENALTY} — the cancellation fee computed from the applicable window;
 *   <li>{@link #SUPPLIER} — what is owed to the supplier/marketplace (irrecoverable under
 *       ALL_SALES_FINAL — the merchant trap);
 *   <li>{@link #CUSTOMER_REFUND} — what is refunded to the customer (a commercial decision);
 *   <li>{@link #NO_SHOW} — the no-show fee.
 * </ul>
 */
public enum ChargeKind {
  PENALTY,
  SUPPLIER,
  CUSTOMER_REFUND,
  NO_SHOW
}
