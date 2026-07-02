package com.fksoft.infra.integration.quotationsite;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the webhook HMAC-SHA256 signature verifier (SPEC-0009 BR3, DL-0016; anti-replay
 * DL-0122). The signature covers {@code timestamp + "." + body}, and a timestamp outside the
 * tolerance window is rejected as replay.
 */
class QuotationSiteSignatureVerifierTest {

  private static final String SECRET = "test-secret";
  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final QuotationSiteSignatureVerifier verifier =
      new QuotationSiteSignatureVerifier(SECRET, 300, clock);

  private static final String TS = NOW.toString();
  private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

  @Test
  void acceptsAValidSignature() {
    String signature = verifier.sign(TS, BODY);
    assertThatCode(() -> verifier.verify(BODY, TS, signature)).doesNotThrowAnyException();
  }

  @Test
  void acceptsAValidSignatureWithSha256Prefix() {
    String signature = "sha256=" + verifier.sign(TS, BODY);
    assertThatCode(() -> verifier.verify(BODY, TS, signature)).doesNotThrowAnyException();
  }

  @Test
  void rejectsAMissingSignature() {
    assertThatThrownBy(() -> verifier.verify(BODY, TS, null))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
    assertThatThrownBy(() -> verifier.verify(BODY, TS, "  "))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsATamperedBody() {
    String signature = verifier.sign(TS, BODY);
    byte[] tampered = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> verifier.verify(tampered, TS, signature))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsAWrongSecret() {
    String foreign = new QuotationSiteSignatureVerifier("other-secret", 300, clock).sign(TS, BODY);
    assertThatThrownBy(() -> verifier.verify(BODY, TS, foreign))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsANonHexSignature() {
    assertThatThrownBy(() -> verifier.verify(BODY, TS, "not-hex!!"))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsAStaleTimestampEvenWithAValidSignature() {
    // A validly-signed body from 10 minutes ago (outside the 5-minute window) is a replay.
    String staleTs = NOW.minus(Duration.ofMinutes(10)).toString();
    String signature = verifier.sign(staleTs, BODY);
    assertThatThrownBy(() -> verifier.verify(BODY, staleTs, signature))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsAMissingTimestamp() {
    String signature = verifier.sign(TS, BODY);
    assertThatThrownBy(() -> verifier.verify(BODY, null, signature))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }
}
