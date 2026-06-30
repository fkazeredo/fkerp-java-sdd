package com.fksoft.infra.platform;

import com.fksoft.domain.platform.SecretCipher;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM adapter for the {@link SecretCipher} port (SPEC-0023 BR1/DL-0074): the certificate
 * material is encrypted at rest with an authenticated cipher (AEAD). The master key is injected
 * from the environment ({@code PLATFORM_SECRET_KEY}, 32 bytes base64) and lives <strong>outside the
 * database</strong> — the stored ciphertext is useless without it. Each encryption uses a fresh
 * random 96-bit IV; the self-describing output is {@code iv || ciphertext || tag} (the 128-bit GCM
 * tag is appended by the JCE). GCM authentication detects any tampering on decrypt.
 *
 * <p>This adapter NEVER logs the plaintext, the ciphertext or the key (security.md). When the dev
 * default key is in use it warns loudly — production MUST set a real key (and, when the owner
 * decides, swap this adapter for a cloud KMS/HSM behind the same port).
 */
@Slf4j
@Component
public class AesGcmSecretCipher implements SecretCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12; // 96-bit IV recommended for GCM (NIST SP 800-38D)
  private static final int TAG_BITS = 128;
  private static final String DEV_DEFAULT_KEY_BASE64 =
      "ZGV2LW9ubHktcGxhdGZvcm0tc2VjcmV0LWtleS0zMmI="; // 32 bytes, dev only — NOT for production

  private final SecretKeySpec key;
  private final String keyAlias;
  private final SecureRandom random = new SecureRandom();

  public AesGcmSecretCipher(
      @Value("${platform.secret.key:}") String configuredKeyBase64,
      @Value("${platform.secret.key-alias:platform-default}") String keyAlias) {
    String base64 = configuredKeyBase64 == null ? "" : configuredKeyBase64.trim();
    if (base64.isEmpty()) {
      log.warn(
          "PLATFORM_SECRET_KEY not set — using the DEV-ONLY certificate custody key. "
              + "Set a real 32-byte base64 key in production (DL-0074).");
      base64 = DEV_DEFAULT_KEY_BASE64;
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64);
    if (keyBytes.length != 32) {
      throw new IllegalStateException(
          "platform.secret.key must be 32 bytes (AES-256) base64-encoded");
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
    this.keyAlias = keyAlias;
  }

  @Override
  public byte[] encrypt(byte[] plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext);
      byte[] out = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
      return out;
    } catch (Exception failure) {
      // Never include the plaintext/key in the message.
      throw new IllegalStateException("secret encryption failed", failure);
    }
  }

  @Override
  public byte[] decrypt(byte[] envelope) {
    try {
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(envelope, 0, iv, 0, IV_BYTES);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return cipher.doFinal(envelope, IV_BYTES, envelope.length - IV_BYTES);
    } catch (Exception failure) {
      // GCM auth failure / wrong key — never leak the material or key.
      throw new IllegalStateException("secret decryption failed", failure);
    }
  }

  @Override
  public String keyAlias() {
    return keyAlias;
  }
}
