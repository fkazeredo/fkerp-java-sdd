package com.fksoft.domain.billing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the municipality rejects the NFS-e issuance (SPEC-0016 BR7, Error Behavior): the data
 * was transmitted but refused (e.g. invalid service code, taker data). Carries the rejection reason
 * as a message argument. Mapped to {@code 422 Unprocessable Entity} — never reported as issued.
 */
public class BillingMunicipalityRejectedException extends DomainException {

  /**
   * @param reason the municipality's rejection reason (surfaced to the user via the i18n message)
   */
  public BillingMunicipalityRejectedException(String reason) {
    super("billing.municipality.rejected", reason);
  }
}
