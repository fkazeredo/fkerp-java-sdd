package com.fksoft.domain.payout;

/**
 * The immediate acknowledgement of a {@link PaymentGateway#request} (SPEC-0017; ADR 0006): the
 * provider's reference and the PENDING status. The final outcome arrives later by webhook — there
 * is no synchronous success here.
 *
 * @param providerRef the provider's payment reference (idempotency key part for the webhook)
 * @param pending always {@code true} for a freshly requested payment (kept explicit for clarity)
 */
public record PaymentRequestResult(String providerRef, boolean pending) {

  /** A PENDING acknowledgement carrying the provider's reference. */
  public static PaymentRequestResult pending(String providerRef) {
    return new PaymentRequestResult(providerRef, true);
  }
}
