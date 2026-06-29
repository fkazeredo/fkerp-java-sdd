package com.fksoft.infra.integration.nfse;

/**
 * The <strong>external</strong> response shape of the municipal NFS-e webservice (SPEC-0016;
 * DL-0046). Used only inside this ACL adapter; it never leaks into the domain. A successful
 * response carries the municipal number and verification code; a rejection carries a reason. The
 * adapter validates and translates it to the domain {@code NfseIssuance} (success) or a classified
 * {@code NfseTransmissionException} (failure).
 *
 * @param accepted whether the municipality accepted the RPS
 * @param nfseNumber the issued NFS-e number (when accepted)
 * @param verificationCode the verification code (when accepted)
 * @param rejectionReason the rejection reason (when not accepted)
 */
public record MunicipalNfseResponse(
    boolean accepted, String nfseNumber, String verificationCode, String rejectionReason) {}
