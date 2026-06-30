package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Custodied e-CNPJ certificate aggregate (SPEC-0023 BR1/BR5; DL-0074). It holds the certificate
 * <strong>metadata in clear</strong> (subject, holder, fingerprint, validity, status) and the
 * secret material <strong>only as encrypted bytes</strong> ({@code encryptedMaterial}, AES-256-GCM
 * envelope) — the private key/password is NEVER stored, returned or logged in clear (BR1,
 * security.md). The {@link #toView} projection is deliberately secret-free; the encrypted material
 * is exposed only to the signer adapter via {@link #encryptedMaterial()} so it can decrypt-and-sign
 * without the material ever leaving custody.
 *
 * <p>Module-internal (Spring Modulith): only the {@code platform} domain reaches it.
 */
@Entity
@Table(name = "platform_certificates")
@Getter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class PlatformCertificate {

  @Id private UUID id;

  private String subject;
  private String holderDocument;
  private String fingerprint;
  private LocalDate validFrom;
  private LocalDate validUntil;

  @Enumerated(EnumType.STRING)
  private CertificateStatus status;

  private byte[] encryptedMaterial;
  private String keyAlias;
  private Instant expirySignaledAt;
  private Instant createdAt;
  private String createdBy;

  @Version private Long version;

  /**
   * Custodies a new certificate (BR1): the material is already encrypted by the caller via the
   * {@code SecretCipher} (DL-0074); this aggregate never sees nor stores it in clear.
   *
   * @param subject the certificate subject DN (metadata, required)
   * @param holderDocument the holder CNPJ (metadata, required)
   * @param fingerprint the SHA-256 thumbprint (required, unique)
   * @param validFrom validity start (required)
   * @param validUntil validity end (required, after {@code validFrom})
   * @param encryptedMaterial the AES-GCM ciphertext of the secret material (required)
   * @param keyAlias the master-key alias that encrypted it
   * @param now the custody instant (UTC)
   * @param actor who imports it (audit)
   * @return a new, persistable VALID certificate (status resolved against {@code now})
   */
  public static PlatformCertificate custody(
      String subject,
      String holderDocument,
      String fingerprint,
      LocalDate validFrom,
      LocalDate validUntil,
      byte[] encryptedMaterial,
      String keyAlias,
      Instant now,
      String actor) {
    PlatformCertificate certificate = new PlatformCertificate();
    certificate.id = UUID.randomUUID();
    certificate.subject = subject;
    certificate.holderDocument = holderDocument;
    certificate.fingerprint = fingerprint;
    certificate.validFrom = validFrom;
    certificate.validUntil = validUntil;
    certificate.encryptedMaterial = encryptedMaterial.clone();
    certificate.keyAlias = keyAlias;
    certificate.createdAt = now;
    certificate.createdBy = actor;
    certificate.status = deriveStatus(validUntil, now, DEFAULT_EXPIRY_HORIZON_DAYS);
    return certificate;
  }

  /** Default horizon (days) used to mark a still-valid certificate as EXPIRING. */
  public static final int DEFAULT_EXPIRY_HORIZON_DAYS = 30;

  private static CertificateStatus deriveStatus(
      LocalDate validUntil, Instant now, int horizonDays) {
    LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    if (!validUntil.isBefore(today) && daysBetween(today, validUntil) <= horizonDays) {
      return CertificateStatus.EXPIRING;
    }
    if (validUntil.isBefore(today)) {
      return CertificateStatus.EXPIRED;
    }
    return CertificateStatus.VALID;
  }

  private static long daysBetween(LocalDate from, LocalDate to) {
    return ChronoUnit.DAYS.between(from, to);
  }

  /** The certificate id. */
  public UUID id() {
    return id;
  }

  /** The SHA-256 thumbprint (identification, not secret). */
  public String fingerprint() {
    return fingerprint;
  }

  /** The validity end date. */
  public LocalDate validUntil() {
    return validUntil;
  }

  /** Whether the expiry alert was already raised (idempotency guard for BR5). */
  public boolean expiryAlreadySignaled() {
    return expirySignaledAt != null;
  }

  /**
   * The encrypted secret material (AES-GCM ciphertext). Exposed ONLY to the signer adapter so it
   * can decrypt-and-sign in custody; it is never the plaintext and is never put in a
   * view/event/log.
   */
  public byte[] encryptedMaterial() {
    return encryptedMaterial.clone();
  }

  /** The master-key alias that encrypted the material (rotation). */
  public String keyAlias() {
    return keyAlias;
  }

  /**
   * Recomputes and applies the lifecycle status against the evaluation instant and horizon (BR5).
   *
   * @return the (possibly updated) status
   */
  public CertificateStatus refreshStatus(Instant now, int horizonDays) {
    if (status != CertificateStatus.REVOKED) {
      this.status = deriveStatus(validUntil, now, horizonDays);
    }
    return status;
  }

  /** Marks the expiry alert as raised (idempotency, BR5). */
  public void markExpirySignaled(Instant when) {
    this.expirySignaledAt = when;
  }

  /** Whole days from {@code now} (UTC date) to the validity end (may be negative). */
  public long daysToExpiry(Instant now) {
    LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    return daysBetween(today, validUntil);
  }

  /**
   * Projects the aggregate to its <strong>secret-free</strong> public view (BR1): metadata only,
   * the status and days-to-expiry resolved against {@code now}. The material is never included.
   */
  public CertificateView toView(Instant now) {
    return new CertificateView(
        subject, holderDocument, fingerprint, validFrom, validUntil, daysToExpiry(now), status);
  }
}
