package com.fksoft.infra.integration.nfse;

import java.math.BigDecimal;

/**
 * The <strong>external JSON</strong> request/response shapes of the emulated municipal NFS-e
 * webservice (SPEC-0016; Fase 19e, DL-0127). Used only inside this ACL adapter over HTTP; they
 * never leak into the domain (ArchUnit boundary test). The real municipality speaks XML/SOAP — this
 * ABRASF-like JSON is the contract the {@code HttpMunicipalNfseService} exchanges with the
 * emulator, and the same seam a real client adapter would fill.
 */
public final class MunicipalNfseHttpMessages {

  private MunicipalNfseHttpMessages() {}

  /** Outbound request body: the signed RPS (base64) plus the taxable data. */
  public record IssueRequest(
      String rpsNumber,
      String municipalityCode,
      String serviceCode,
      BigDecimal serviceAmount,
      BigDecimal issAmount,
      String currency,
      String signedRpsBase64) {}

  /** Inbound response body: acceptance + number/verification code, or a rejection reason. */
  public record IssueResponse(
      boolean accepted, String nfseNumber, String verificationCode, String rejectionReason) {}

  /** Outbound cancel request body. */
  public record CancelRequest(String nfseNumber, String reason) {}
}
