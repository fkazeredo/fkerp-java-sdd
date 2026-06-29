package com.fksoft.domain.billing;

/**
 * Technical exception raised by the {@link NfseGateway} adapter when the municipal transmission
 * fails (SPEC-0016 BR7). It carries the {@link NfseFailureClass} so the issuance flow can translate
 * it into the right business exception (REJECTED → {@link BillingMunicipalityRejectedException} /
 * 422; TIMEOUT/UNAVAILABLE → {@link BillingNfseWebserviceException} / 502). It is a plain runtime
 * exception (not a {@code DomainException}) because it is an integration/transport fault, not a
 * business rule — the flow classifies it.
 */
public class NfseTransmissionException extends RuntimeException {

  private final transient NfseFailureClass failureClass;

  /**
   * @param failureClass the failure classification (BR7)
   * @param reason a non-sensitive description (rejection reason or transport detail)
   */
  public NfseTransmissionException(NfseFailureClass failureClass, String reason) {
    super(reason);
    this.failureClass = failureClass;
  }

  /** The failure classification (BR7). */
  public NfseFailureClass failureClass() {
    return failureClass;
  }
}
