package com.fksoft.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Test-only JWT minting + verification (SPEC-0024 Phase 13 / DL-0105). Mirrors what the external
 * IdP (Keycloak) does, with a <strong>local RSA keypair</strong> so the test suite validates the
 * genuine JWKS/RS256 path — signature, {@code iss}, {@code exp} and the {@code realm_access.roles}
 * mapping — <strong>without an internet IdP</strong> and without a heavyweight Keycloak container.
 *
 * <p>A single keypair is generated once per JVM; {@link #decoder()} builds a {@link JwtDecoder}
 * from the public key (the same RS256 verification the resource server performs), and {@link #mint}
 * signs a token with the private key in the Keycloak claim shape ({@code preferred_username},
 * {@code realm_access.roles}, {@code sub}).
 */
public final class TestJwtTokens {

  /** The expected issuer in tests (matches the test profile's resource-server config). */
  public static final String ISSUER = "https://test-idp.local/realms/acme";

  private static final KeyPair KEY_PAIR = generateKeyPair();

  private TestJwtTokens() {}

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation failed in tests", e);
    }
  }

  /**
   * The {@link JwtDecoder} the test resource server uses — verifies RS256 with the test public key.
   */
  public static JwtDecoder decoder() {
    return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
        .signatureAlgorithm(SignatureAlgorithm.RS256)
        .build();
  }

  /**
   * Mints a signed RS256 token for {@code username} carrying {@code roles} in {@code
   * realm_access.roles} (Keycloak shape), a random stable {@code sub}, and a valid 5-minute window.
   *
   * @param username the {@code preferred_username}
   * @param roles the realm roles (e.g. {@code ROLE_FINANCE})
   * @return the compact serialized JWT
   */
  public static String mint(String username, String... roles) {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", username)
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .claim("scope", "openid profile")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)))
            .build();
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
      jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Test token signing failed", e);
    }
  }
}
