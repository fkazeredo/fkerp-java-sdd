package com.fksoft.domain.platform;

import com.fksoft.domain.platform.internal.PlatformCertificate;
import com.fksoft.domain.platform.internal.PlatformCertificateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the e-CNPJ certificate custody (SPEC-0023 BR1/BR5; DL-0074). It is the
 * single entry point to the custody: importing encrypts the material at rest via the {@link
 * SecretCipher} port, the status exposes <strong>only metadata</strong> (never the material), and
 * the expiry sweep raises {@link CertificateExpiring} for the governance/audit. The decrypted
 * material is handed out ONLY to the signer adapter ({@link #loadActiveMaterial()}), never returned
 * by an API or logged (BR1, security.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateCustodyService {

  private final PlatformCertificateRepository certificateRepository;
  private final SecretCipher secretCipher;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Custodies a new e-CNPJ certificate (BR1): the secret material is encrypted-at-rest immediately
   * (DL-0074) and never persisted/logged in clear. Only metadata is kept readable.
   *
   * @param command the certificate metadata + secret material
   * @param actor who imports it (audit)
   * @return the secret-free metadata view of the custodied certificate
   */
  @Transactional
  public CertificateView importCertificate(ImportCertificateCommand command, String actor) {
    Instant now = clock.instant();
    byte[] encrypted = secretCipher.encrypt(command.material());
    String fingerprint = CertificateFingerprint.of(command.material());
    PlatformCertificate certificate =
        PlatformCertificate.custody(
            command.subject(),
            command.holderDocument(),
            fingerprint,
            command.validFrom(),
            command.validUntil(),
            encrypted,
            secretCipher.keyAlias(),
            now,
            actor);
    certificateRepository.save(certificate);
    log.info(
        "CertificateCustodied fingerprint={} holder={} validUntil={} by={}",
        fingerprint,
        CertificateFingerprint.maskCnpj(command.holderDocument()),
        command.validUntil(),
        actor);
    return certificate.toView(now);
  }

  /**
   * The current certificate status (BR1): metadata only — subject, validity, days-to-expiry,
   * status. The certificate material is NEVER returned.
   *
   * @return the secret-free status view
   * @throws CertificateNotFoundException when no certificate is custodied
   */
  @Transactional(readOnly = true)
  public CertificateView status() {
    Instant now = clock.instant();
    return certificateRepository
        .findFirstByOrderByCreatedAtDesc()
        .map(certificate -> certificate.toView(now))
        .orElseThrow(CertificateNotFoundException::new);
  }

  /**
   * Flags certificates within the expiry horizon and raises {@link CertificateExpiring} once each
   * (BR5, idempotent by {@code expirySignaledAt}). Driven by a controlled-clock job (DL-0076), so
   * it is testable with a fixed instant.
   *
   * @param now the evaluation instant
   * @param horizonDays how many days ahead of expiry to alert
   * @return how many certificates were newly flagged
   */
  @Transactional
  public int flagExpiringCertificates(Instant now, int horizonDays) {
    List<PlatformCertificate> certificates = certificateRepository.findAllByOrderByCreatedAtDesc();
    int flagged = 0;
    for (PlatformCertificate certificate : certificates) {
      CertificateStatus status = certificate.refreshStatus(now, horizonDays);
      boolean expiringOrExpired =
          status == CertificateStatus.EXPIRING || status == CertificateStatus.EXPIRED;
      if (expiringOrExpired && !certificate.expiryAlreadySignaled()) {
        certificate.markExpirySignaled(now);
        certificateRepository.save(certificate);
        events.publishEvent(
            new CertificateExpiring(
                certificate.fingerprint(),
                certificate.validUntil(),
                certificate.daysToExpiry(now),
                now));
        log.info(
            "CertificateExpiring fingerprint={} validUntil={} daysToExpiry={}",
            certificate.fingerprint(),
            certificate.validUntil(),
            certificate.daysToExpiry(now));
        flagged++;
      } else {
        certificateRepository.save(certificate);
      }
    }
    return flagged;
  }

  /**
   * Loads and decrypts the active certificate's secret material for the signer adapter ONLY (BR1).
   * The plaintext is returned to the in-process signer and never leaves custody via an API/log.
   *
   * @return the decrypted secret material
   * @throws CertificateUnavailableException when no certificate is custodied or it cannot be
   *     decrypted (the message never exposes the material or key)
   */
  @Transactional(readOnly = true)
  public byte[] loadActiveMaterial() {
    PlatformCertificate certificate =
        certificateRepository
            .findFirstByOrderByCreatedAtDesc()
            .orElseThrow(CertificateUnavailableException::new);
    try {
      return secretCipher.decrypt(certificate.encryptedMaterial());
    } catch (RuntimeException cannotDecrypt) {
      // Never leak the material/key: log only the fingerprint and re-raise a controlled error.
      log.error(
          "CertificateCustodyUnavailable fingerprint={} (decrypt failed)",
          certificate.fingerprint());
      throw new CertificateUnavailableException();
    }
  }

  /** Whether a certificate is currently custodied (for the signer's fallback decision). */
  @Transactional(readOnly = true)
  public boolean hasCertificate() {
    return certificateRepository.findFirstByOrderByCreatedAtDesc().isPresent();
  }
}
