package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.infra.security.PersistedJwk;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the persisted Authorization Server signing key (Fase 19g, DL-0129/ADR-0020). The
 * HA property under test: two "instances" loading the SAME configured private key produce the SAME
 * JWK (kid + public key), so a token minted by one instance validates against the other's JWKS —
 * and a restart no longer invalidates every session.
 */
class PersistedJwkTest {

  @Test
  void twoInstancesLoadingTheSameConfiguredKeyShareKidAndPublicKey() throws Exception {
    String configured = generatePkcs8Base64();

    RSAKey instanceA = PersistedJwk.load(configured, "erp-signing-key");
    RSAKey instanceB = PersistedJwk.load(configured, "erp-signing-key");

    assertThat(instanceA.getKeyID()).isEqualTo("erp-signing-key");
    assertThat(instanceB.getKeyID()).isEqualTo(instanceA.getKeyID());
    assertThat(instanceB.toRSAPublicKey().getModulus())
        .isEqualTo(instanceA.toRSAPublicKey().getModulus());
    assertThat(instanceB.toRSAPublicKey().getPublicExponent())
        .isEqualTo(instanceA.toRSAPublicKey().getPublicExponent());
  }

  @Test
  void aBlankKidIsDerivedDeterministicallyFromTheKey() throws Exception {
    String configured = generatePkcs8Base64();

    RSAKey first = PersistedJwk.load(configured, "");
    RSAKey second = PersistedJwk.load(configured, null);

    assertThat(first.getKeyID()).isNotBlank();
    assertThat(second.getKeyID()).isEqualTo(first.getKeyID());
  }

  @Test
  void withoutAConfiguredKeyEachLoadIsEphemeralAndDistinct() {
    RSAKey first = PersistedJwk.load("", null);
    RSAKey second = PersistedJwk.load(null, null);

    assertThat(first.getKeyID()).isNotEqualTo(second.getKeyID());
  }

  @Test
  void invalidKeyMaterialFailsFastNamingThePropertyOnly() {
    assertThatThrownBy(() -> PersistedJwk.load("not-base64-!!", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.oidc.jwk.private-key")
        // Never leak the material in the message.
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("not-base64"));
  }

  private static String generatePkcs8Base64() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
  }
}
