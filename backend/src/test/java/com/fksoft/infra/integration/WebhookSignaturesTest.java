package com.fksoft.infra.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the shared webhook HMAC + anti-replay helper (Fase 19c, DL-0122). */
class WebhookSignaturesTest {

  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
  private static final String TS = NOW.toString();
  private static final byte[] BODY = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);

  private final WebhookSignatures signatures =
      new WebhookSignatures("secret", Duration.ofMinutes(5));

  @Test
  void acceptsAFreshValidSignature() {
    String sig = signatures.sign(TS, BODY);
    assertThat(signatures.verify(BODY, TS, sig, NOW)).isEqualTo(WebhookSignatures.Result.OK);
  }

  @Test
  void rejectsAMissingOrMalformedSignature() {
    assertThat(signatures.verify(BODY, TS, null, NOW))
        .isEqualTo(WebhookSignatures.Result.INVALID_SIGNATURE);
    assertThat(signatures.verify(BODY, TS, "not-hex!!", NOW))
        .isEqualTo(WebhookSignatures.Result.INVALID_SIGNATURE);
  }

  @Test
  void rejectsATamperedBody() {
    String sig = signatures.sign(TS, BODY);
    byte[] tampered = "{\"x\":2}".getBytes(StandardCharsets.UTF_8);
    assertThat(signatures.verify(tampered, TS, sig, NOW))
        .isEqualTo(WebhookSignatures.Result.INVALID_SIGNATURE);
  }

  @Test
  void rejectsAStaleTimestampAsReplay() {
    String staleTs = NOW.minus(Duration.ofMinutes(10)).toString();
    String sig = signatures.sign(staleTs, BODY);
    assertThat(signatures.verify(BODY, staleTs, sig, NOW))
        .isEqualTo(WebhookSignatures.Result.REPLAY);
  }

  @Test
  void rejectsAFutureTimestampAsReplay() {
    String futureTs = NOW.plus(Duration.ofMinutes(10)).toString();
    String sig = signatures.sign(futureTs, BODY);
    assertThat(signatures.verify(BODY, futureTs, sig, NOW))
        .isEqualTo(WebhookSignatures.Result.REPLAY);
  }

  @Test
  void rejectsAMissingOrMalformedTimestampAsReplay() {
    String sig = signatures.sign(TS, BODY);
    assertThat(signatures.verify(BODY, null, sig, NOW)).isEqualTo(WebhookSignatures.Result.REPLAY);
    assertThat(signatures.verify(BODY, "not-a-date", sig, NOW))
        .isEqualTo(WebhookSignatures.Result.REPLAY);
  }

  @Test
  void aSignatureIsBoundToItsTimestamp() {
    // A signature minted for one timestamp does not validate the body under a different timestamp.
    String sig = signatures.sign(TS, BODY);
    String otherTs = NOW.minus(Duration.ofMinutes(1)).toString();
    assertThat(signatures.verify(BODY, otherTs, sig, NOW))
        .isEqualTo(WebhookSignatures.Result.INVALID_SIGNATURE);
  }
}
