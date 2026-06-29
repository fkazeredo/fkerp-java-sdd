package com.fksoft.domain.accounts;

/**
 * Lifecycle status of a commercial account (BR4). A new account is born {@link #ACTIVE}; status
 * transitions (suspend/reactivate/inactivate) are deliberately out of scope until a real workflow
 * requires them (SPEC-0002 Open Questions). External (API/persistence) value is the constant name.
 */
public enum AccountStatus {
  ACTIVE,
  SUSPENDED,
  INACTIVE
}
