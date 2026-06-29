package com.fksoft.domain.sourcing;

/**
 * Classification of an inbound-integration failure (SPEC-0009 BR5; {@code
 * messaging-and-integrations.md}). A failure is always classified and never produces a misleading
 * business result (no fallback that fakes a price). Used for observability ({@code
 * integration_failures_total{class}}) and recorded against the inbound attempt. External
 * (metric/log) value is the constant name.
 */
public enum IntegrationFailureClass {

  /** The webhook signature was missing or did not match (BR3). */
  SIGNATURE_INVALID,

  /** The external payload was malformed or failed validation. */
  PAYLOAD_INVALID,

  /** No registered account matched the payload's document (DL-0017). */
  ACCOUNT_NOT_FOUND,

  /** A re-delivery of an already-processed {@code externalQuotationId} (BR4 — not an error). */
  DUPLICATE,

  /** Any other, unclassified failure. */
  UNKNOWN_ERROR
}
