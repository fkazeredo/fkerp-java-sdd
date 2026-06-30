package com.fksoft.domain.platform;

/**
 * Port for encrypting/decrypting secret material at rest (SPEC-0023 BR1/DL-0074). The adapter
 * ({@code infra.platform.AesGcmSecretCipher}) uses an authenticated cipher (AES-256-GCM) with the
 * master key held OUTSIDE the database (environment). The domain depends only on this port, so the
 * custody mechanism (envelope cipher today, a cloud KMS/HSM later) can be swapped without touching
 * the model.
 *
 * <p>Implementations MUST NEVER log the plaintext, the ciphertext or the key (security.md). A
 * failed decryption (tampering, wrong/missing key) MUST surface as a controlled failure, never the
 * raw crypto exception leaking the material.
 */
public interface SecretCipher {

  /**
   * Encrypts the given plaintext secret material.
   *
   * @param plaintext the secret bytes (e.g. the PFX/PEM) — never logged
   * @return the self-describing ciphertext ({@code iv || ciphertext || tag})
   */
  byte[] encrypt(byte[] plaintext);

  /**
   * Decrypts material previously produced by {@link #encrypt(byte[])} under the alias' key.
   *
   * @param ciphertext the stored ciphertext
   * @return the plaintext secret bytes — never logged
   */
  byte[] decrypt(byte[] ciphertext);

  /** The alias of the master key this cipher encrypts with (stored for rotation, DL-0074). */
  String keyAlias();
}
