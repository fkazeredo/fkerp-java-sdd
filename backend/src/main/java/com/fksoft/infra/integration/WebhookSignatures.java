package com.fksoft.infra.integration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared HMAC-SHA256 signing/verification for inbound webhooks (SPEC-0009/SPEC-0017; Fase 19c,
 * DL-0122). It removes the duplication between the quotation-site and the payment webhook verifiers
 * and adds an <strong>anti-replay window</strong>: a captured, validly-signed body can otherwise be
 * re-sent forever. The caller passes the request timestamp (an ISO-8601 header value); the
 * signature is computed over {@code timestamp + "." + body} and the timestamp must fall within
 * {@code tolerance} of now — a stale or future timestamp is rejected even with a correct HMAC.
 *
 * <p>Constant-time comparison ({@link MessageDigest#isEqual}) avoids timing attacks. The secret and
 * the raw material are never logged. This is infra-only; the secret lives in configuration, never
 * in the domain.
 */
public final class WebhookSignatures {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String SHA256_PREFIX = "sha256=";

  private final byte[] secret;
  private final Duration tolerance;

  /**
   * @param secret the shared HMAC key
   * @param tolerance how far the signed timestamp may be from now (anti-replay window)
   */
  public WebhookSignatures(String secret, Duration tolerance) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.tolerance = tolerance;
  }

  /** Outcome of a verification attempt (the caller maps it to its own domain exception). */
  public enum Result {
    /** Signature valid and timestamp within the window. */
    OK,
    /** Header absent/blank, malformed hex, or HMAC mismatch. */
    INVALID_SIGNATURE,
    /** Timestamp absent/malformed, or outside the anti-replay window. */
    REPLAY
  }

  /**
   * Verifies the signature over {@code timestamp + "." + rawBody} and the anti-replay window.
   *
   * @param rawBody the exact bytes received
   * @param timestampHeader the request timestamp header (ISO-8601 instant); required for
   *     anti-replay
   * @param signatureHeader the signature header (hex, optional {@code sha256=} prefix)
   * @param now the current instant
   * @return the verification result
   */
  public Result verify(
      byte[] rawBody, String timestampHeader, String signatureHeader, Instant now) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      return Result.INVALID_SIGNATURE;
    }
    Instant timestamp = parseTimestamp(timestampHeader);
    if (timestamp == null || outsideWindow(timestamp, now)) {
      return Result.REPLAY;
    }
    byte[] expected = hmac(signedPayload(timestampHeader.trim(), rawBody));
    byte[] provided = decodeHexOrNull(stripPrefix(signatureHeader.trim()));
    if (provided == null || !MessageDigest.isEqual(expected, provided)) {
      return Result.INVALID_SIGNATURE;
    }
    return Result.OK;
  }

  /** Signs {@code timestamp + "." + rawBody} — used by the traceable mocks/tests to sign. */
  public String sign(String timestamp, byte[] rawBody) {
    return HexFormat.of().formatHex(hmac(signedPayload(timestamp, rawBody)));
  }

  private boolean outsideWindow(Instant timestamp, Instant now) {
    return Duration.between(timestamp, now).abs().compareTo(tolerance) > 0;
  }

  private static byte[] signedPayload(String timestamp, byte[] rawBody) {
    byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
    byte[] payload = new byte[prefix.length + rawBody.length];
    System.arraycopy(prefix, 0, payload, 0, prefix.length);
    System.arraycopy(rawBody, 0, payload, prefix.length, rawBody.length);
    return payload;
  }

  private byte[] hmac(byte[] payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(payload);
    } catch (GeneralSecurityException e) {
      // Misconfiguration of the algorithm/key is not a client error; do not leak details.
      throw new IllegalStateException("cannot compute HMAC");
    }
  }

  private static Instant parseTimestamp(String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(header.trim());
    } catch (java.time.format.DateTimeParseException invalid) {
      return null;
    }
  }

  private static String stripPrefix(String header) {
    if (header.regionMatches(true, 0, SHA256_PREFIX, 0, SHA256_PREFIX.length())) {
      return header.substring(SHA256_PREFIX.length());
    }
    return header;
  }

  private static byte[] decodeHexOrNull(String hex) {
    try {
      return HexFormat.of().parseHex(hex);
    } catch (IllegalArgumentException invalidHex) {
      return null;
    }
  }
}
