package com.fksoft.domain.marketing;

import com.fksoft.domain.marketing.internal.Consent;
import com.fksoft.domain.marketing.internal.ConsentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Marketing module (SPEC-0019). Slice 8f-1 covers the
 * <strong>consent</strong> use cases (BR1): granting and revoking append immutable rows to the
 * consent log, the current state is the latest row per subject+purpose (DL-0056), and the history
 * is preserved. The send filter (BR2), segments/campaigns (BR3/BR4) and attribution/erasure
 * (BR5/BR6) extend this facade in later slices.
 *
 * <p>Consent is a first-class citizen (security.md/LGPD): subject ids are masked in logs (only the
 * subject type and purpose are logged), the revocation publishes {@link ConsentRevoked} for future
 * suppression, and the source free-text is the only place PII could appear — never echoed in
 * errors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingService {

  private final ConsentRepository consentRepository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Records a consent grant as a new immutable row (BR1/DL-0056).
   *
   * @param command the grant (subject, purpose, legal basis, source)
   * @param actor who records it (audit)
   * @return the persisted row view
   * @throws ConsentInvalidException when required data is missing (BR1)
   */
  @Transactional
  public ConsentView grantConsent(GrantConsentCommand command, String actor) {
    if (command == null) {
      throw new ConsentInvalidException();
    }
    Instant now = clock.instant();
    Consent consent =
        Consent.record(
            command.subject(),
            command.purpose(),
            command.legalBasis(),
            ConsentStatus.GRANTED,
            command.source(),
            now,
            actor);
    consentRepository.save(consent);
    log.info(
        "ConsentGranted consentId={} subjectType={} purpose={} grantedBy={}",
        consent.id(),
        consent.subjectType(),
        consent.purpose(),
        actor);
    return consent.toView();
  }

  /**
   * Revokes the current consent for a subject+purpose by appending a REVOKED row (BR1/DL-0056). The
   * row id passed in identifies an existing consent row of that subject+purpose (typically the
   * current GRANTED row); the revocation is a new row, never an update. Publishes {@link
   * ConsentRevoked} for future suppression (BR2).
   *
   * @param consentId an existing consent row id of the subject+purpose to revoke
   * @param actor who revokes it (audit)
   * @return the new REVOKED row view
   * @throws ConsentNotFoundException when the row does not exist
   */
  @Transactional
  public ConsentView revokeConsent(java.util.UUID consentId, String actor) {
    Consent existing =
        consentRepository.findById(consentId).orElseThrow(ConsentNotFoundException::new);
    Instant now = clock.instant();
    Consent revocation =
        Consent.record(
            new SubjectRef(existing.subjectId(), existing.subjectType()),
            existing.purpose(),
            existing.legalBasis(),
            ConsentStatus.REVOKED,
            null,
            now,
            actor);
    consentRepository.save(revocation);
    events.publishEvent(
        new ConsentRevoked(existing.subjectId(), existing.subjectType(), existing.purpose(), now));
    log.info(
        "ConsentRevoked consentId={} subjectType={} purpose={} revokedBy={}",
        revocation.id(),
        existing.subjectType(),
        existing.purpose(),
        actor);
    return revocation.toView();
  }

  /**
   * The current consent state for a subject+purpose (DL-0056): the projection of the latest row, or
   * {@link ConsentStatus#REVOKED} semantics (not granted) when there is no row.
   *
   * @param subject the subject (value)
   * @param purpose the purpose
   * @return the current state; {@code currentStatus=REVOKED} with a {@code null} timestamp when no
   *     consent was ever recorded (so the send filter treats unknown subjects as not-consented)
   */
  @Transactional(readOnly = true)
  public ConsentState currentState(SubjectRef subject, ConsentPurpose purpose) {
    return consentRepository
        .findLatest(subject.type(), subject.id(), purpose)
        .map(
            row ->
                new ConsentState(
                    new SubjectRef(row.subjectId(), row.subjectType()),
                    row.purpose(),
                    row.status(),
                    row.createdAt()))
        .orElse(new ConsentState(subject, purpose, ConsentStatus.REVOKED, null));
  }

  /** The full append-only history for a subject+purpose, newest first (BR1). */
  @Transactional(readOnly = true)
  public List<ConsentView> history(SubjectRef subject, ConsentPurpose purpose) {
    return consentRepository.findHistory(subject.type(), subject.id(), purpose).stream()
        .map(Consent::toView)
        .toList();
  }
}
