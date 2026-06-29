package com.fksoft.domain.booking;

/**
 * Who bears the cost of a cancellation charge (SPEC-0010 BR5). External value is the constant name.
 *
 * <ul>
 *   <li>{@link #AGENCY} — the agency/agent absorbs it (e.g. a STANDARD penalty passed down);
 *   <li>{@link #ACME} — Acme absorbs it (merchant-of-record case, BR8/DL-0021);
 *   <li>{@link #SUPPLIER} — the supplier/marketplace absorbs it (affiliate default).
 * </ul>
 */
public enum CostBearer {
  AGENCY,
  ACME,
  SUPPLIER
}
