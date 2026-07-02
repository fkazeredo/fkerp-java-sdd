package com.fksoft.infra.integration.nfse;

import com.fksoft.domain.billing.CertificateSigner;
import com.fksoft.domain.billing.NfseCancellation;
import com.fksoft.domain.billing.NfseFailureClass;
import com.fksoft.domain.billing.NfseGateway;
import com.fksoft.domain.billing.NfseIssuance;
import com.fksoft.domain.billing.NfseIssueRequest;
import com.fksoft.domain.billing.NfseTransmissionException;
import com.fksoft.domain.money.Money;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The Anti-Corruption Layer adapter for the municipal NFS-e webservice (SPEC-0016 BR3/BR6/BR7;
 * DL-0046), implementing the domain {@link NfseGateway}. It translates the domain {@link
 * NfseIssueRequest} into the external {@link MunicipalNfseEnvelope} (ABRASF-like RPS), signs it
 * with the e-CNPJ ({@link CertificateSigner}), "transmits" it, validates the {@link
 * MunicipalNfseResponse} and translates it back to the domain {@link NfseIssuance}. The vendor
 * shape stays in this package (an ArchUnit boundary test proves it never reaches the domain).
 *
 * <p>The real municipal webservice is out of scope; this is the <strong>traceable mock</strong> of
 * that integration ({@code simulation-and-mocking.md}). It is deterministic and supports
 * <strong>fault injection</strong> via a recognizable municipality code so tests can exercise the
 * failure paths (BR7) without a live dependency: {@code "REJECT"} → REJECTED (422), {@code
 * "TIMEOUT"} → TIMEOUT (502), {@code "UNAVAILABLE"} → UNAVAILABLE (502). Every call has a timeout
 * and an integration log (latency, failure class, no sensitive data).
 *
 * <p><strong>Adapter selection (Fase 19e, DL-0127):</strong> this simulated adapter is the
 * <strong>default</strong> ({@code billing.nfse.adapter=simulated} or unset). Setting {@code
 * billing.nfse.adapter=http} swaps in {@link HttpMunicipalNfseService}, a genuine HTTP client that
 * exercises real timeout/retry/circuit-breaker against the emulator (and, later, a real
 * municipality) — same port, no domain change.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "billing.nfse.adapter",
    havingValue = "simulated",
    matchIfMissing = true)
public class SimulatedMunicipalNfseService implements NfseGateway {

  private final CertificateSigner certificateSigner;
  private final long timeoutMs;

  public SimulatedMunicipalNfseService(
      CertificateSigner certificateSigner,
      @Value("${billing.nfse.timeout-ms:5000}") long timeoutMs) {
    this.certificateSigner = certificateSigner;
    this.timeoutMs = timeoutMs;
  }

  @Override
  public NfseIssuance issue(NfseIssueRequest request) {
    long started = System.nanoTime();
    String municipality = request.municipality();
    // 1) Translate the domain request to the external envelope (ACL).
    MunicipalNfseEnvelope envelope = toEnvelope(request);
    // 2) Sign with the e-CNPJ (the signed XML is what the municipality and the vault receive).
    byte[] signedXml = certificateSigner.sign(buildRpsXml(envelope));
    // 3) "Transmit" and get the external response (the live client is out of scope — DL-0046).
    MunicipalNfseResponse response = transmit(municipality, request.invoiceId(), signedXml);
    long latencyMs = (System.nanoTime() - started) / 1_000_000;
    // 4) Validate + translate the external response back to the domain (response validation, BR7).
    if (!response.accepted()) {
      log.info(
          "NfseTransmission invoiceId={} municipality={} class=REJECTED latencyMs={}",
          request.invoiceId(),
          municipality,
          latencyMs);
      throw new NfseTransmissionException(NfseFailureClass.REJECTED, response.rejectionReason());
    }
    if (isBlank(response.nfseNumber()) || isBlank(response.verificationCode())) {
      // Never report a false "issued": an accepted response without number/code is invalid.
      log.info(
          "NfseTransmission invoiceId={} municipality={} class=UNAVAILABLE (invalid response) latencyMs={}",
          request.invoiceId(),
          municipality,
          latencyMs);
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "municipal response missing number/verification code");
    }
    log.info(
        "NfseIssued invoiceId={} municipality={} number={} latencyMs={}",
        request.invoiceId(),
        municipality,
        response.nfseNumber(),
        latencyMs);
    return new NfseIssuance(
        response.nfseNumber(), response.verificationCode(), signedXml, "application/xml");
  }

  @Override
  public void cancel(NfseCancellation cancellation) {
    if (triggersFailure(cancellation.number())) {
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "cancel failed (simulated)");
    }
    log.info(
        "NfseCancelled invoiceId={} number={} (simulated municipality)",
        cancellation.invoiceId(),
        cancellation.number());
  }

  private MunicipalNfseEnvelope toEnvelope(NfseIssueRequest request) {
    Money base = request.commissionBase();
    return new MunicipalNfseEnvelope(
        "RPS-" + request.invoiceId(),
        request.municipality(),
        request.serviceCode(),
        base.amount(),
        request.iss().amount(),
        base.currency(),
        new byte[0]);
  }

  private static byte[] buildRpsXml(MunicipalNfseEnvelope envelope) {
    // A minimal ABRASF-like RPS XML (the exact municipal schema is out of scope — DL-0046).
    String xml =
        "<Rps><Numero>"
            + envelope.rpsNumber()
            + "</Numero><Municipio>"
            + envelope.municipalityCode()
            + "</Municipio><CodigoServico>"
            + envelope.serviceCode()
            + "</CodigoServico><ValorServicos>"
            + envelope.serviceAmount().toPlainString()
            + "</ValorServicos><ValorIss>"
            + envelope.issAmount().toPlainString()
            + "</ValorIss></Rps>";
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Simulates the municipal transmission. Deterministic by design (DL-0046): a recognizable
   * municipality code injects a failure so the failure paths (BR7) are testable; otherwise it
   * returns a deterministic acceptance with a number derived from the invoice id.
   */
  private MunicipalNfseResponse transmit(String municipality, UUID invoiceId, byte[] signedXml) {
    if ("TIMEOUT".equalsIgnoreCase(municipality)) {
      throw new NfseTransmissionException(
          NfseFailureClass.TIMEOUT, "municipal webservice timed out after " + timeoutMs + "ms");
    }
    if ("UNAVAILABLE".equalsIgnoreCase(municipality)) {
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "municipal webservice unavailable");
    }
    if ("REJECT".equalsIgnoreCase(municipality)) {
      return new MunicipalNfseResponse(false, null, null, "código de serviço inválido");
    }
    String suffix = invoiceId.toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    return new MunicipalNfseResponse(true, "2026/" + suffix, "VC-" + suffix, null);
  }

  private static boolean triggersFailure(String value) {
    return value != null && value.contains("FAILCANCEL");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
