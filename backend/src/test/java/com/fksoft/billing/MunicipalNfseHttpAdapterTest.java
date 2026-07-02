package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.billing.CertificateSigner;
import com.fksoft.domain.billing.NfseFailureClass;
import com.fksoft.domain.billing.NfseIssuance;
import com.fksoft.domain.billing.NfseIssueRequest;
import com.fksoft.domain.billing.NfseTransmissionException;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.integration.OutboundCircuitBreaker;
import com.fksoft.infra.integration.nfse.HttpMunicipalNfseService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Real-HTTP tests for the municipal NFS-e ACL adapter (SPEC-0016 BR3/BR7; Fase 19e, DL-0127). A
 * tiny JDK {@link HttpServer} stands in for the municipal webservice (no new dependency), so the
 * adapter's genuine HTTP path is exercised: a success translates to an {@link NfseIssuance}; a
 * {@code 422} is a REJECTED verdict; a slow endpoint is a TIMEOUT that the breaker eventually trips
 * OPEN. The reflection-free constructor injects a {@link RestClient} pointed at the emulator and a
 * controlled-clock {@link OutboundCircuitBreaker}.
 */
class MunicipalNfseHttpAdapterTest {

  private static HttpServer server;
  private static volatile String mode = "ok";
  private static final AtomicInteger issueCalls = new AtomicInteger(0);

  private final CertificateSigner signer = xml -> ("SIGNED:" + new String(xml)).getBytes();

  @BeforeAll
  static void startEmulator() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    // A thread pool so slow (timeout-mode) handlers run concurrently instead of queueing — the
    // retries each reach the emulator (deterministic call count) and never bleed into the next
    // test.
    server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
    server.createContext(
        "/nfse/issue",
        exchange -> {
          issueCalls.incrementAndGet();
          switch (mode) {
            case "reject" ->
                respond(
                    exchange, 422, "{\"accepted\":false,\"rejectionReason\":\"código inválido\"}");
            case "timeout" -> {
              sleep(800); // longer than the adapter read-timeout (400ms)
              respond(exchange, 200, "{}"); // responds after the client already timed out
            }
            case "unavailable" -> respond(exchange, 503, "{}");
            default ->
                respond(
                    exchange,
                    200,
                    "{\"accepted\":true,\"nfseNumber\":\"2026/000123\",\"verificationCode\":\"VC-XYZ\"}");
          }
        });
    server.start();
  }

  @AfterAll
  static void stopEmulator() {
    server.stop(0);
  }

  @BeforeEach
  void reset() {
    mode = "ok";
    issueCalls.set(0);
  }

  private HttpMunicipalNfseService adapter(int maxRetries, OutboundCircuitBreaker breaker) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(500));
    factory.setReadTimeout(Duration.ofMillis(400));
    RestClient client =
        RestClient.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .requestFactory(factory)
            .build();
    return new HttpMunicipalNfseService(
        signer, new tools.jackson.databind.ObjectMapper(), client, maxRetries, breaker);
  }

  private OutboundCircuitBreaker breaker(int threshold) {
    return new OutboundCircuitBreaker(
        "test-nfse", threshold, Duration.ofSeconds(60), Clock.systemUTC());
  }

  @Test
  void aSuccessfulTransmissionTranslatesToADomainIssuance() {
    mode = "ok";
    NfseIssuance issuance = adapter(0, breaker(5)).issue(request());

    assertThat(issuance.number()).isEqualTo("2026/000123");
    assertThat(issuance.verificationCode()).isEqualTo("VC-XYZ");
    assertThat(new String(issuance.signedDocument())).startsWith("SIGNED:");
  }

  @Test
  void aMunicipalRejectionIsAClassifiedRejectedFailure() {
    mode = "reject";
    assertThatThrownBy(() -> adapter(0, breaker(5)).issue(request()))
        .isInstanceOf(NfseTransmissionException.class)
        .extracting(e -> ((NfseTransmissionException) e).failureClass())
        .isEqualTo(NfseFailureClass.REJECTED);
    // A business rejection is terminal — not retried.
    assertThat(issueCalls.get()).isEqualTo(1);
  }

  @Test
  void aSlowEndpointIsATimeoutAndIsRetried() {
    mode = "timeout";
    assertThatThrownBy(() -> adapter(2, breaker(99)).issue(request()))
        .isInstanceOf(NfseTransmissionException.class)
        .extracting(e -> ((NfseTransmissionException) e).failureClass())
        .isEqualTo(NfseFailureClass.TIMEOUT);
    // 1 initial + 2 retries = 3 transmission attempts.
    assertThat(issueCalls.get()).isEqualTo(3);
  }

  @Test
  void repeatedFailuresTripTheCircuitBreakerOpen() {
    mode = "unavailable";
    OutboundCircuitBreaker breaker = breaker(3);
    HttpMunicipalNfseService adapter = adapter(0, breaker);

    // Three consecutive UNAVAILABLE failures trip the breaker.
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> adapter.issue(request()))
          .isInstanceOf(NfseTransmissionException.class);
    }
    assertThat(breaker.state()).isEqualTo(OutboundCircuitBreaker.State.OPEN);

    // While OPEN, the next call short-circuits without hitting the emulator.
    int before = issueCalls.get();
    assertThatThrownBy(() -> adapter.issue(request()))
        .isInstanceOf(OutboundCircuitBreaker.CircuitOpenException.class);
    assertThat(issueCalls.get()).isEqualTo(before);
  }

  @Test
  void theBreakerRecoversAfterCooldown() {
    Instant now = Instant.parse("2026-07-02T12:00:00Z");
    var clock = new MutableClock(now);
    OutboundCircuitBreaker breaker =
        new OutboundCircuitBreaker("recover", 1, Duration.ofSeconds(30), clock);
    breaker.recordFailure();
    assertThat(breaker.state()).isEqualTo(OutboundCircuitBreaker.State.OPEN);

    clock.advance(Duration.ofSeconds(31));
    assertThat(breaker.allowRequest()).isTrue(); // HALF_OPEN trial allowed
    breaker.recordSuccess();
    assertThat(breaker.state()).isEqualTo(OutboundCircuitBreaker.State.CLOSED);
  }

  private static NfseIssueRequest request() {
    return new NfseIssueRequest(
        UUID.randomUUID(),
        Money.of(new BigDecimal("135.00"), "BRL"),
        Money.of(new BigDecimal("6.75"), "BRL"),
        "3550308",
        "0107");
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** A hand-advanced clock for the cooldown test. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration by) {
      this.now = this.now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
