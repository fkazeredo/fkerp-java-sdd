package com.fksoft.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity/security configuration (SPEC-0024/DL-0079). The in-house JWT signing secret, lifetime
 * and issuer come from the environment — the secret is NEVER hardcoded or logged (BR4). In
 * production a real 32+ byte {@code IDENTITY_JWT_SECRET} MUST be set; the dev default is used only
 * when unset and is NOT secure.
 */
@ConfigurationProperties(prefix = "identity")
public class SecurityProperties {

  private final Jwt jwt = new Jwt();

  public Jwt getJwt() {
    return jwt;
  }

  /** JWT issuance/verification settings. */
  public static class Jwt {

    /** HMAC-SHA256 signing secret (>= 32 bytes). Dev default only when unset — not secure. */
    private String secret = "dev-identity-jwt-secret-change-me-please-32b!";

    /** Token lifetime in seconds (default 8 hours). */
    private long ttlSeconds = 28_800;

    /** The {@code iss} claim / expected issuer. */
    private String issuer = "acme-travel-erp";

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public long getTtlSeconds() {
      return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
    }

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }
  }
}
