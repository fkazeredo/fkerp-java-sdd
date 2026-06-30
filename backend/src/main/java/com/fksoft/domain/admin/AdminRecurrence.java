package com.fksoft.domain.admin;

/**
 * Recurrence of an administrative contract's recurring charge (SPEC-0025 BR2): monthly (the common
 * case for utilities/subscriptions), yearly, or other. Optional — a one-off contract may carry
 * none.
 */
public enum AdminRecurrence {
  MONTHLY,
  YEARLY,
  OTHER
}
