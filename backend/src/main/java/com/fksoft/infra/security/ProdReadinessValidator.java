package com.fksoft.infra.security;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails the application fast in the {@code prod} profile when a dev-only secret or an insecure
 * default is still in place (SPEC-0024/SPEC-0016 — Fase 19c, DL-0123). The whole system is
 * "production-shaped" but the shared HMAC secrets, the certificate master key and the DB password
 * all have dev defaults that would otherwise silently apply in production; and a real NFS-e must
 * not be issued before the accountant confirms the tax regime (DL-0121). This validator turns those
 * latent risks into a <strong>startup failure with an explicit message</strong>, so a misconfigured
 * production boot is impossible rather than quietly insecure.
 *
 * <p>Only active in {@code prod}: dev/test/e2e keep their convenient defaults. It runs on {@link
 * ApplicationReadyEvent} and throws {@link IllegalStateException} listing every offending setting.
 * No secret value is logged — only the property name and the reason.
 */
@Slf4j
@Component
@Profile("prod")
public class ProdReadinessValidator implements ApplicationListener<ApplicationReadyEvent> {

  static final String QUOTATION_SECRET_DEFAULT = "dev-quotation-site-secret";
  static final String PAYMENT_SECRET_DEFAULT = "dev-payment-webhook-secret";
  static final String DB_PASSWORD_DEFAULT = "acme";

  private final String quotationSecret;
  private final String paymentSecret;
  private final String platformKey;
  private final String dbPassword;
  private final String issuerUri;
  private final boolean billingRegimeConfirmed;
  private final String oidcJwkPrivateKey;

  public ProdReadinessValidator(
      @Value("${integration.quotation-site.secret:}") String quotationSecret,
      @Value("${integration.payment.webhook-secret:}") String paymentSecret,
      @Value("${platform.secret.key:}") String platformKey,
      @Value("${spring.datasource.password:}") String dbPassword,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
      @Value("${billing.tax.regime-confirmed:false}") boolean billingRegimeConfirmed,
      @Value("${app.oidc.jwk.private-key:}") String oidcJwkPrivateKey) {
    this.quotationSecret = quotationSecret;
    this.paymentSecret = paymentSecret;
    this.platformKey = platformKey;
    this.dbPassword = dbPassword;
    this.issuerUri = issuerUri;
    this.billingRegimeConfirmed = billingRegimeConfirmed;
    this.oidcJwkPrivateKey = oidcJwkPrivateKey;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    List<String> problems = new ArrayList<>();

    if (isBlankOrEquals(quotationSecret, QUOTATION_SECRET_DEFAULT)) {
      problems.add(
          "integration.quotation-site.secret is unset or the dev default — set a strong shared"
              + " secret");
    }
    if (isBlankOrEquals(paymentSecret, PAYMENT_SECRET_DEFAULT)) {
      problems.add(
          "integration.payment.webhook-secret is unset or the dev default — set a strong shared"
              + " secret");
    }
    if (isBlank(platformKey)) {
      problems.add(
          "platform.secret.key (PLATFORM_SECRET_KEY) is unset — a 32-byte base64 master key is"
              + " required to custody the e-CNPJ certificate at rest");
    }
    if (isBlankOrEquals(dbPassword, DB_PASSWORD_DEFAULT)) {
      problems.add(
          "spring.datasource.password is unset or the dev default — set a real DB password");
    }
    if (issuerUri != null && issuerUri.startsWith("http://")) {
      problems.add(
          "the OIDC issuer-uri is plain http — production must serve the Authorization Server over"
              + " https (OIDC_ISSUER_URI)");
    }
    if (!billingRegimeConfirmed) {
      problems.add(
          "billing.tax.regime-confirmed is false — the accountant must confirm the tax regime"
              + " (Anexo III×V / Fator R, DL-0121) before a real NFS-e can be issued in production");
    }
    if (isBlank(oidcJwkPrivateKey)) {
      problems.add(
          "app.oidc.jwk.private-key (OIDC_JWK_PRIVATE_KEY) is unset — production must use a"
              + " persisted signing key (DL-0129/ADR-0020) or every restart/replica invalidates all"
              + " sessions/tokens");
    }

    if (!problems.isEmpty()) {
      String message =
          "Refusing to run in the 'prod' profile with insecure defaults ("
              + problems.size()
              + "):\n  - "
              + String.join("\n  - ", problems);
      throw new IllegalStateException(message);
    }
    log.info("prod readiness check passed: no dev defaults or insecure settings detected");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isBlankOrEquals(String value, String forbidden) {
    return isBlank(value) || forbidden.equals(value);
  }
}
