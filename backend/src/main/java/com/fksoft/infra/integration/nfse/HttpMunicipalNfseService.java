package com.fksoft.infra.integration.nfse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.domain.billing.CertificateSigner;
import com.fksoft.domain.billing.NfseCancellation;
import com.fksoft.domain.billing.NfseFailureClass;
import com.fksoft.domain.billing.NfseGateway;
import com.fksoft.domain.billing.NfseIssuance;
import com.fksoft.domain.billing.NfseIssueRequest;
import com.fksoft.domain.billing.NfseTransmissionException;
import com.fksoft.infra.integration.OutboundCircuitBreaker;
import com.fksoft.infra.integration.nfse.MunicipalNfseHttpMessages.CancelRequest;
import com.fksoft.infra.integration.nfse.MunicipalNfseHttpMessages.IssueRequest;
import com.fksoft.infra.integration.nfse.MunicipalNfseHttpMessages.IssueResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The <strong>real HTTP</strong> Anti-Corruption Layer adapter for the municipal NFS-e webservice
 * (SPEC-0016 BR3/BR6/BR7; Fase 19e, DL-0127), implementing {@link NfseGateway}. Unlike the shipped
 * {@link SimulatedMunicipalNfseService} (an in-process mock), this one performs a genuine HTTP call
 * — so timeout, retry and the circuit breaker are actually exercised (against the emulator in dev/
 * tests; against a real municipality once credentials/homologation exist). It is active only when
 * {@code billing.nfse.adapter=http}; the simulated adapter stays the default.
 *
 * <p>Flow: translate the domain {@link NfseIssueRequest} → external {@link IssueRequest} (ACL, the
 * vendor shape never crosses into the domain), sign the RPS with the e-CNPJ ({@link
 * CertificateSigner}), POST it through a {@link RestClient} with connect/read timeouts, retry the
 * transient failures up to {@code max-retries}, all guarded by an {@link OutboundCircuitBreaker};
 * then validate + translate the response back to a domain {@link NfseIssuance} or a classified
 * {@link NfseTransmissionException} (BR7: TIMEOUT/UNAVAILABLE → 502, REJECTED → 422). A false
 * "issued" is impossible: an accepted response missing the number/code is treated as UNAVAILABLE.
 *
 * <p>JSON is (de)serialized with the shared {@link ObjectMapper} and exchanged via {@code
 * .exchange(...)} so the adapter fully controls status handling and body parsing (independent of
 * the RestClient's default message-converter set).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billing.nfse.adapter", havingValue = "http")
public class HttpMunicipalNfseService implements NfseGateway {

  private final CertificateSigner certificateSigner;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final int maxRetries;
  private final OutboundCircuitBreaker breaker;

  public HttpMunicipalNfseService(
      CertificateSigner certificateSigner,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${billing.nfse.base-url:http://localhost:8090}") String baseUrl,
      @Value("${billing.nfse.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${billing.nfse.read-timeout-ms:5000}") long readTimeoutMs,
      @Value("${billing.nfse.max-retries:2}") int maxRetries,
      @Value("${billing.nfse.breaker.failure-threshold:5}") int failureThreshold,
      @Value("${billing.nfse.breaker.cooldown-ms:60000}") long cooldownMs) {
    this.certificateSigner = certificateSigner;
    this.objectMapper = objectMapper;
    this.maxRetries = Math.max(0, maxRetries);
    this.breaker =
        new OutboundCircuitBreaker(
            "municipal-nfse", failureThreshold, Duration.ofMillis(cooldownMs), clock);
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  /** Constructor for tests: injects a preconfigured {@link RestClient} pointed at the emulator. */
  public HttpMunicipalNfseService(
      CertificateSigner certificateSigner,
      ObjectMapper objectMapper,
      RestClient restClient,
      int maxRetries,
      OutboundCircuitBreaker breaker) {
    this.certificateSigner = certificateSigner;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
    this.maxRetries = Math.max(0, maxRetries);
    this.breaker = breaker;
  }

  @Override
  public NfseIssuance issue(NfseIssueRequest request) {
    byte[] signedRps = certificateSigner.sign(buildRpsXml(request));
    IssueRequest body = toRequest(request, signedRps);
    IssueResponse response =
        postWithResilience("/nfse/issue", body, IssueResponse.class, request.municipality());

    if (!response.accepted()) {
      throw new NfseTransmissionException(NfseFailureClass.REJECTED, response.rejectionReason());
    }
    if (isBlank(response.nfseNumber()) || isBlank(response.verificationCode())) {
      // Never a false "issued": an accepted response without number/code is invalid.
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "municipal response missing number/verification code");
    }
    log.info(
        "NfseIssued(http) invoiceId={} municipality={} number={}",
        request.invoiceId(),
        request.municipality(),
        response.nfseNumber());
    return new NfseIssuance(
        response.nfseNumber(), response.verificationCode(), signedRps, "application/xml");
  }

  @Override
  public void cancel(NfseCancellation cancellation) {
    postWithResilience(
        "/nfse/cancel", new CancelRequest(cancellation.number(), "cancelled"), Void.class, null);
    log.info(
        "NfseCancelled(http) invoiceId={} number={}",
        cancellation.invoiceId(),
        cancellation.number());
  }

  /**
   * POSTs with the circuit breaker, timeouts and bounded retries. Transient failures
   * (TIMEOUT/UNAVAILABLE) are retried; a REJECTED (422) is terminal (retrying a business rejection
   * is pointless). Every attempt records success/failure into the breaker.
   */
  private <T> T postWithResilience(String path, Object body, Class<T> type, String municipality) {
    breaker.guard();
    String json = serialize(body);
    NfseTransmissionException last = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        T result = exchange(path, json, type);
        breaker.recordSuccess();
        return result;
      } catch (NfseTransmissionException classified) {
        if (classified.failureClass() == NfseFailureClass.REJECTED) {
          // A business verdict, not an outage: the service answered — don't trip the breaker,
          // don't retry.
          breaker.recordSuccess();
          throw classified;
        }
        last = classified;
        breaker.recordFailure();
      }
      if (attempt < maxRetries) {
        log.info(
            "NfseTransmission retry municipality={} path={} attempt={} class={}",
            municipality,
            path,
            attempt + 1,
            last.failureClass());
      }
    }
    throw last;
  }

  /**
   * One HTTP attempt via {@code .exchange(...)}: on 2xx parses the body to {@code type}; on 422
   * raises a REJECTED; on any other 4xx/5xx raises UNAVAILABLE; a read timeout / connect failure
   * surfaces as {@link ResourceAccessException} and is classified as TIMEOUT/UNAVAILABLE.
   */
  private <T> T exchange(String path, String json, Class<T> type) {
    try {
      return restClient
          .post()
          .uri(path)
          .contentType(MediaType.APPLICATION_JSON)
          .body(json)
          .exchange(
              (req, response) -> {
                HttpStatusCode status = response.getStatusCode();
                String responseBody = readBody(response.getBody());
                if (status.is2xxSuccessful()) {
                  return type == Void.class ? null : deserialize(responseBody, type);
                }
                if (status.value() == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
                  throw new NfseTransmissionException(NfseFailureClass.REJECTED, responseBody);
                }
                throw new NfseTransmissionException(
                    NfseFailureClass.UNAVAILABLE, "municipal HTTP " + status.value());
              });
    } catch (ResourceAccessException access) {
      if (access.getCause() instanceof SocketTimeoutException) {
        throw new NfseTransmissionException(
            NfseFailureClass.TIMEOUT, "municipal webservice timed out");
      }
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "municipal webservice unavailable");
    }
  }

  private String serialize(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new NfseTransmissionException(NfseFailureClass.UNAVAILABLE, "cannot serialize request");
    }
  }

  private <T> T deserialize(String responseBody, Class<T> type) {
    try {
      return objectMapper.readValue(responseBody, type);
    } catch (IOException malformed) {
      throw new NfseTransmissionException(
          NfseFailureClass.UNAVAILABLE, "malformed municipal response");
    }
  }

  private static String readBody(java.io.InputStream in) {
    try {
      return in == null ? "" : StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException io) {
      return "";
    }
  }

  private static IssueRequest toRequest(NfseIssueRequest request, byte[] signedRps) {
    return new IssueRequest(
        "RPS-" + request.invoiceId(),
        request.municipality(),
        request.serviceCode(),
        request.commissionBase().amount(),
        request.iss().amount(),
        request.commissionBase().currency(),
        Base64.getEncoder().encodeToString(signedRps));
  }

  private static byte[] buildRpsXml(NfseIssueRequest request) {
    String xml =
        "<Rps><Numero>RPS-"
            + request.invoiceId()
            + "</Numero><Municipio>"
            + request.municipality()
            + "</Municipio><CodigoServico>"
            + request.serviceCode()
            + "</CodigoServico><ValorServicos>"
            + request.commissionBase().amount().toPlainString()
            + "</ValorServicos><ValorIss>"
            + request.iss().amount().toPlainString()
            + "</ValorIss></Rps>";
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
