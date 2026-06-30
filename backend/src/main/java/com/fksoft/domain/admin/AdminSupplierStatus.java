package com.fksoft.domain.admin;

/**
 * Lifecycle status of an administrative supplier (SPEC-0025 BR1): {@code ACTIVE} when it can take
 * new contracts/expenses, {@code INACTIVE} once deactivated. A supplier is born {@code ACTIVE}.
 */
public enum AdminSupplierStatus {
  ACTIVE,
  INACTIVE
}
