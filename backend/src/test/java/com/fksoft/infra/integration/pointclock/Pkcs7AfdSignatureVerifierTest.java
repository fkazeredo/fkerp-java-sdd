package com.fksoft.infra.integration.pointclock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the CAdES/PKCS#7 AFD signature/integrity verifier (SPEC-0012 BR4; DL-0032).
 * Proves: a well-formed signed envelope whose content matches the declared hash is accepted; a
 * tampered content (hash mismatch) is rejected; an unsigned envelope (no SignerInfo) is rejected; a
 * file that is not a PKCS#7 envelope is rejected; null/empty inputs are rejected.
 */
class Pkcs7AfdSignatureVerifierTest {

  private final Pkcs7AfdSignatureVerifier verifier = new Pkcs7AfdSignatureVerifier();

  private static final byte[] AFD_CONTENT =
      ("00000000012026062603100000000000000000000000123456789012"
              + "ACME TRAVEL LTDA AFD NSR-ORDERED-RECORDS")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void acceptsAWellFormedSignedEnvelopeWithMatchingContentHash() {
    byte[] envelope = AfdEnvelopeFixtures.signedEnvelope(AFD_CONTENT);
    String expected = AfdEnvelopeFixtures.sha256(AFD_CONTENT);

    assertThat(verifier.verify(envelope, expected)).isTrue();
  }

  @Test
  void rejectsTamperedContentWhoseHashDoesNotMatch() {
    byte[] tampered =
        AfdEnvelopeFixtures.signedEnvelope("TAMPERED-AFD-BYTES".getBytes(StandardCharsets.UTF_8));
    // The declared hash is of the ORIGINAL content — the envelope was tampered.
    String expectedOfOriginal = AfdEnvelopeFixtures.sha256(AFD_CONTENT);

    assertThat(verifier.verify(tampered, expectedOfOriginal)).isFalse();
  }

  @Test
  void rejectsAnUnsignedEnvelopeWithoutSignerInfo() {
    // Content deliberately free of the 0x31 ('1') byte so the SignerInfo approximation is
    // exercised.
    byte[] content = "ESPELHO DE PONTO SEM ASSINATURA".getBytes(StandardCharsets.UTF_8);
    byte[] envelope = AfdEnvelopeFixtures.unsignedEnvelope(content);

    assertThat(verifier.verify(envelope, AfdEnvelopeFixtures.sha256(content))).isFalse();
  }

  @Test
  void rejectsAFileThatIsNotAPkcs7Envelope() {
    byte[] plain = AfdEnvelopeFixtures.notAnEnvelope();

    assertThat(verifier.verify(plain, AfdEnvelopeFixtures.sha256(plain))).isFalse();
  }

  @Test
  void rejectsNullOrEmptyInput() {
    assertThat(verifier.verify(null, "sha256:abc")).isFalse();
    assertThat(verifier.verify(new byte[0], "sha256:abc")).isFalse();
    assertThat(verifier.verify(AfdEnvelopeFixtures.signedEnvelope(AFD_CONTENT), null)).isFalse();
  }
}
