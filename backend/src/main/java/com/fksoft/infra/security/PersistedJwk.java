package com.fksoft.infra.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Loads the Authorization Server's RSA signing key from configuration (Fase 19g, DL-0129 /
 * ADR-0020) so restarts and replicas share the SAME key: tokens survive a restart and any instance
 * validates any instance's tokens. The configured material is a <strong>base64 DER PKCS#8 RSA
 * private key</strong> ({@code OIDC_JWK_PRIVATE_KEY}) with a stable {@code kid} ({@code
 * OIDC_JWK_KEY_ID}); the public key is derived from the private CRT key. When unset, a fresh
 * ephemeral key is generated (dev/test convenience — the pre-19g behavior); production requires the
 * persisted key (ProdReadinessValidator).
 *
 * <p>Generate a key pair once (never logged/committed):
 *
 * <pre>{@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 \
 *   -topk8 -nocrypt -outform DER | base64 -w0}</pre>
 */
public final class PersistedJwk {

  private PersistedJwk() {}

  /**
   * Builds the signing {@link RSAKey}. With a configured private key, the same material (and {@code
   * kid}) is produced on every instance; otherwise an ephemeral key pair is generated.
   *
   * @param privateKeyBase64 base64 DER PKCS#8 RSA private key, or null/blank for ephemeral
   * @param keyId the stable kid to publish (used only with a configured key; blank → derived)
   * @return the RSA JWK (private + public)
   */
  public static RSAKey load(String privateKeyBase64, String keyId) {
    if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
      return ephemeral();
    }
    try {
      byte[] der = Base64.getDecoder().decode(privateKeyBase64.trim());
      KeyFactory factory = KeyFactory.getInstance("RSA");
      RSAPrivateCrtKey privateKey =
          (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
      RSAPublicKey publicKey =
          (RSAPublicKey)
              factory.generatePublic(
                  new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
      String kid =
          (keyId == null || keyId.isBlank())
              // A deterministic kid derived from the modulus, so every instance publishes the same.
              ? Integer.toHexString(privateKey.getModulus().hashCode())
              : keyId.trim();
      return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
    } catch (IllegalArgumentException | java.security.GeneralSecurityException invalid) {
      // Never log the material; the message names only the property.
      throw new IllegalStateException(
          "app.oidc.jwk.private-key (OIDC_JWK_PRIVATE_KEY) is not a valid base64 DER PKCS#8 RSA"
              + " private key",
          invalid);
    }
  }

  private static RSAKey ephemeral() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey(pair.getPrivate())
          .keyID(UUID.randomUUID().toString())
          .build();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation failed for the authorization server", e);
    }
  }
}
