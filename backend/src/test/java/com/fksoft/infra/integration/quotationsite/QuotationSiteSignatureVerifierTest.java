package com.fksoft.infra.integration.quotationsite;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for the webhook HMAC-SHA256 signature verifier (SPEC-0009 BR3, DL-0016). */
class QuotationSiteSignatureVerifierTest {

  private static final String SECRET = "test-secret";
  private final QuotationSiteSignatureVerifier verifier =
      new QuotationSiteSignatureVerifier(SECRET);

  private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

  @Test
  void acceptsAValidSignature() {
    String signature = verifier.sign(BODY);
    assertThatCode(() -> verifier.verify(BODY, signature)).doesNotThrowAnyException();
  }

  @Test
  void acceptsAValidSignatureWithSha256Prefix() {
    String signature = "sha256=" + verifier.sign(BODY);
    assertThatCode(() -> verifier.verify(BODY, signature)).doesNotThrowAnyException();
  }

  @Test
  void rejectsAMissingSignature() {
    assertThatThrownBy(() -> verifier.verify(BODY, null))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
    assertThatThrownBy(() -> verifier.verify(BODY, "  "))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsATamperedBody() {
    String signature = verifier.sign(BODY);
    byte[] tampered = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> verifier.verify(tampered, signature))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsAWrongSecret() {
    String foreign = new QuotationSiteSignatureVerifier("other-secret").sign(BODY);
    assertThatThrownBy(() -> verifier.verify(BODY, foreign))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsANonHexSignature() {
    assertThatThrownBy(() -> verifier.verify(BODY, "not-hex!!"))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }
}
