package com.fksoft.domain.payout;

/**
 * Domain port to the external payment provider (SPEC-0017 Scope "execução via porta de pagamento —
 * ACL"; ADR 0006; DL-0048). It is an Anti-Corruption Layer boundary: implementations (in {@code
 * com.fksoft.infra.integration.payment}) translate the provider's shape to/from these domain types,
 * sign/verify the asynchronous webhook, apply a timeout and classify failures. The provider DTO
 * never crosses into the domain (enforced by an ArchUnit boundary test).
 *
 * <p>Following ADR 0006, {@link #request} returns <strong>immediately</strong> with a {@code
 * providerRef} and status {@code PENDING}; the final outcome ({@code SUCCEEDED}/{@code FAILED})
 * arrives <strong>asynchronously via webhook</strong> — there is no synchronous "paid". The shipped
 * adapter is a traceable mock; a real provider is a new adapter, no domain change.
 */
public interface PaymentGateway {

  /**
   * Requests a payment for one payout installment (BR2). Returns immediately with the provider's
   * reference and a PENDING status; the confirmation/failure is delivered later by webhook.
   *
   * @param instruction what to pay (payout/installment, amount, outcome hint for the mock)
   * @return the provider's reference and the PENDING acknowledgement
   * @throws PayoutGatewayException when the request leg itself fails (timeout/unavailable/invalid)
   *     — classified, so the caller never sees a false "executed" (BR2)
   */
  PaymentRequestResult request(PaymentInstruction instruction);
}
