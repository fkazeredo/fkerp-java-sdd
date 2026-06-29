package com.fksoft.domain.billing;

/**
 * Classification of a municipal NFS-e webservice failure (SPEC-0016 BR7; {@code
 * messaging-and-integrations.md}). A failed transmission is never reported as an issued invoice;
 * the class drives the HTTP mapping: {@link #REJECTED} (the municipality refused the data) → 422;
 * {@link #TIMEOUT}/{@link #UNAVAILABLE} (transport/availability) → 502.
 */
public enum NfseFailureClass {
  TIMEOUT,
  UNAVAILABLE,
  REJECTED
}
