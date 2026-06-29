package com.fksoft.domain.billing;

/**
 * Domain port to the municipal NFS-e webservice (SPEC-0016 BR3/BR6/BR7; DL-0046). It is an
 * Anti-Corruption Layer boundary: implementations (in {@code com.fksoft.infra.integration.nfse})
 * translate the municipal vendor's XML/SOAP shape to/from these domain types, sign the payload with
 * the e-CNPJ ({@link CertificateSigner}), apply a timeout, classify failures and validate the
 * response. The vendor DTO never crosses into the domain (enforced by an ArchUnit boundary test).
 * The real municipal webservice is out of scope; the shipped adapter is a traceable mock.
 */
public interface NfseGateway {

  /**
   * Issues the NFS-e at the municipality (BR3): signs and transmits, returning the
   * number/verification code and the signed document on success.
   *
   * @param request the domain issue request
   * @return the successful issuance (number, code, signed document)
   * @throws NfseTransmissionException when the municipality rejects or the webservice fails (BR7),
   *     carrying the {@link NfseFailureClass}
   */
  NfseIssuance issue(NfseIssueRequest request);

  /**
   * Cancels an issued NFS-e at the municipality (BR6).
   *
   * @param cancellation the domain cancellation request
   * @throws NfseTransmissionException when the municipality rejects or the webservice fails (BR7)
   */
  void cancel(NfseCancellation cancellation);
}
