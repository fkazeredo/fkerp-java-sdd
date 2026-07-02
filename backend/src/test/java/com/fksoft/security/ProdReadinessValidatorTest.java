package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.infra.security.ProdReadinessValidator;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the production-readiness fail-fast (SPEC-0024/SPEC-0016 Fase 19c, DL-0123). The
 * validator is exercised directly (no {@code prod} context boot): it must refuse the dev defaults
 * and pass when every secret is real, https and the tax regime is confirmed.
 */
class ProdReadinessValidatorTest {

  private static final String JWK = "aBase64Pkcs8PrivateKeyMaterial";

  private static ProdReadinessValidator validator(
      String quotationSecret,
      String paymentSecret,
      String platformKey,
      String dbPassword,
      String issuer,
      boolean regimeConfirmed) {
    return new ProdReadinessValidator(
        quotationSecret, paymentSecret, platformKey, dbPassword, issuer, regimeConfirmed, JWK);
  }

  private static ProdReadinessValidator allGood() {
    return validator(
        "a-strong-quotation-secret",
        "a-strong-payment-secret",
        "bXktMzItYnl0ZS1iYXNlNjQta2V5LW1hdGVyaWFsLXg=",
        "a-real-db-password",
        "https://erp.acme.example",
        true);
  }

  @Test
  void passesWhenEverySecretIsRealHttpsAndRegimeConfirmed() {
    assertThatCode(() -> allGood().onApplicationEvent(null)).doesNotThrowAnyException();
  }

  @Test
  void failsWithoutAPersistedSigningKey() {
    // Fase 19g (DL-0129): an ephemeral AS key in production invalidates all tokens on restart.
    ProdReadinessValidator validator =
        new ProdReadinessValidator("q", "p", "key", "pwd", "https://x", true, "");
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.oidc.jwk.private-key");
  }

  @Test
  void failsOnTheDevQuotationSecret() {
    ProdReadinessValidator validator =
        validator(
            "dev-quotation-site-secret",
            "a-strong-payment-secret",
            "key",
            "pwd",
            "https://x",
            true);
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("integration.quotation-site.secret");
  }

  @Test
  void failsOnTheDevPaymentSecretAndDefaultDbPassword() {
    ProdReadinessValidator validator =
        validator("ok-quotation", "dev-payment-webhook-secret", "key", "acme", "https://x", true);
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("integration.payment.webhook-secret")
        .hasMessageContaining("spring.datasource.password");
  }

  @Test
  void failsOnAMissingPlatformKey() {
    ProdReadinessValidator validator = validator("q", "p", "", "pwd", "https://x", true);
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("platform.secret.key");
  }

  @Test
  void failsOnAPlainHttpIssuer() {
    ProdReadinessValidator validator = validator("q", "p", "key", "pwd", "http://insecure", true);
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("issuer-uri is plain http");
  }

  @Test
  void failsWhenTheTaxRegimeIsNotConfirmed() {
    ProdReadinessValidator validator = validator("q", "p", "key", "pwd", "https://x", false);
    assertThatThrownBy(() -> validator.onApplicationEvent(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("billing.tax.regime-confirmed");
  }
}
