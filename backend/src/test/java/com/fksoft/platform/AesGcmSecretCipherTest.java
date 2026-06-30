package com.fksoft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.infra.platform.AesGcmSecretCipher;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the AES-256-GCM secret cipher (SPEC-0023 BR1/DL-0074): it round-trips, produces a
 * different ciphertext each time (random IV), keeps the plaintext out of the ciphertext, and
 * detects tampering (GCM authentication) instead of silently returning corrupt material.
 */
class AesGcmSecretCipherTest {

  private static final String KEY_32_BYTES_BASE64 =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private final AesGcmSecretCipher cipher =
      new AesGcmSecretCipher(KEY_32_BYTES_BASE64, "test-alias");

  @Test
  void roundTripsTheSecretMaterial() {
    byte[] secret = "PRIVATE-KEY-MATERIAL".getBytes(StandardCharsets.UTF_8);

    byte[] encrypted = cipher.encrypt(secret);
    byte[] decrypted = cipher.decrypt(encrypted);

    assertThat(decrypted).isEqualTo(secret);
    // The ciphertext does not contain the plaintext.
    assertThat(new String(encrypted, StandardCharsets.UTF_8)).doesNotContain("PRIVATE-KEY");
    assertThat(cipher.keyAlias()).isEqualTo("test-alias");
  }

  @Test
  void usesAFreshIvSoTheSameSecretEncryptsDifferently() {
    byte[] secret = "same-secret".getBytes(StandardCharsets.UTF_8);

    byte[] first = cipher.encrypt(secret);
    byte[] second = cipher.encrypt(secret);

    assertThat(first).isNotEqualTo(second); // random IV → different envelope
    assertThat(cipher.decrypt(first)).isEqualTo(secret);
    assertThat(cipher.decrypt(second)).isEqualTo(secret);
  }

  @Test
  void detectsTamperingInsteadOfReturningCorruptMaterial() {
    byte[] encrypted = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
    byte[] tampered = encrypted.clone();
    tampered[tampered.length - 1] ^= 0x01; // flip a bit in the GCM tag

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsAKeyThatIsNotThirtyTwoBytes() {
    String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());
    assertThatThrownBy(() -> new AesGcmSecretCipher(shortKey, "alias"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void fallsBackToTheDevKeyWhenUnsetButStillRoundTrips() {
    AesGcmSecretCipher devCipher = new AesGcmSecretCipher("", "dev");
    byte[] secret = "x".getBytes(StandardCharsets.UTF_8);
    assertThat(devCipher.decrypt(devCipher.encrypt(secret))).isEqualTo(secret);
    assertThat(Arrays.equals(devCipher.decrypt(devCipher.encrypt(secret)), secret)).isTrue();
  }
}
