package com.fksoft.domain.marketing;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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
  private final SegmentRepository segmentRepository;
  private final CampaignRepository campaignRepository;
  private final CampaignSendRepository campaignSendRepository;
  private final AttributionRepository attributionRepository;
  private final NewsletterSender newsletterSender;
  private final CadastroValidator cadastroValidator;
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
    // Validate the subject-type and purpose reference codes against the cadastro (SPEC-0031
    // BR3/DL-0116) — an unknown/inactive code is rejected (422) before any write.
    cadastroValidator.validate(CadastroType.MARKETING_SUBJECT_TYPE, command.subject().type());
    cadastroValidator.validate(CadastroType.CONSENT_PURPOSE, command.purpose());
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
  public ConsentState currentState(SubjectRef subject, String purpose) {
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
  public List<ConsentView> history(SubjectRef subject, String purpose) {
    return consentRepository.findHistory(subject.type(), subject.id(), purpose).stream()
        .map(Consent::toView)
        .toList();
  }

  // --- Segments (BR3/DL-0059) ---

  /**
   * Defines a segment from validated criteria (BR3/DL-0059). The criteria are validated against the
   * closed catalog by {@link SegmentCriteria}; an unknown field is a {@link
   * SegmentInvalidException}.
   *
   * @param command the definition (name + raw criteria)
   * @param actor who defines it (audit)
   * @return the persisted segment view
   * @throws SegmentInvalidException when the criteria are unknown or malformed
   */
  @Transactional
  public SegmentView defineSegment(DefineSegmentCommand command, String actor) {
    SegmentCriteria criteria = new SegmentCriteria(command.criteria());
    Instant now = clock.instant();
    Segment segment = Segment.define(command.name(), criteria, now, actor);
    segmentRepository.save(segment);
    log.info(
        "SegmentDefined segmentId={} name={} definedBy={}", segment.id(), command.name(), actor);
    return segment.toView();
  }

  /**
   * Estimates a segment's reach (BR3). In v1 the reach is computed over the consent base the module
   * owns (DL-0059): the subjects with a current GRANTED consent for the newsletter — i.e. the ones
   * a campaign over this segment could actually reach. No new personal data is collected.
   *
   * @param segmentId the segment id
   * @return the number of currently consented, reachable subjects
   * @throws SegmentInvalidException when the segment does not exist
   */
  @Transactional(readOnly = true)
  public long previewSegment(UUID segmentId) {
    segmentRepository.findById(segmentId).orElseThrow(SegmentInvalidException::new);
    return consentedRecipients(MarketingCodes.NEWSLETTER).size();
  }

  // --- Campaigns (BR2/BR4/DL-0055) ---

  /**
   * Creates a campaign over a segment (SPEC-0019). The {@code code} is the unique public
   * attribution token. A duplicate code surfaces as a translated business error, never a raw
   * constraint leak.
   *
   * @param command the campaign details (segment, code, content, window)
   * @param actor who creates it (audit)
   * @return the persisted campaign view
   * @throws SegmentInvalidException when the segment does not exist
   */
  @Transactional
  public CampaignView createCampaign(CreateCampaignCommand command, String actor) {
    segmentRepository.findById(command.segmentId()).orElseThrow(SegmentInvalidException::new);
    Instant now = clock.instant();
    Campaign campaign =
        Campaign.create(
            command.segmentId(),
            command.code(),
            command.contentRef(),
            command.windowFrom(),
            command.windowTo(),
            now,
            actor);
    campaignRepository.save(campaign);
    log.info(
        "CampaignCreated campaignId={} segmentId={} code={} createdBy={}",
        campaign.id(),
        command.segmentId(),
        command.code(),
        actor);
    return campaign.toView();
  }

  /**
   * Dispatches a campaign (BR2/BR4/DL-0055): gathers the candidate recipients, <strong>filters by
   * consent before enqueuing</strong> (BR2) so subjects without a current GRANTED consent are
   * suppressed and counted (never a global error), skips recipients already sent to (BR4
   * idempotency, the {@code campaign_sends} key), sends each remaining message through the {@link
   * NewsletterSender} ACL and records the send. Publishes {@link CampaignSent} with the counts (no
   * PII).
   *
   * @param campaignId the campaign id
   * @param actor who triggers it (audit)
   * @return the send result (targeted / suppressed / queued)
   * @throws CampaignNotFoundException when the campaign does not exist
   */
  @Transactional
  public CampaignSendResult sendCampaign(UUID campaignId, String actor) {
    Campaign campaign =
        campaignRepository.findById(campaignId).orElseThrow(CampaignNotFoundException::new);
    Instant now = clock.instant();
    String purpose = MarketingCodes.NEWSLETTER;

    List<SubjectRef> candidates = candidateRecipients(purpose);
    int targeted = candidates.size();
    int suppressed = 0;
    int queued = 0;

    for (SubjectRef recipient : candidates) {
      if (!currentState(recipient, purpose).isGranted()) {
        suppressed++; // BR2: no GRANTED consent → excluded from the dispatch (filter + count)
        continue;
      }
      if (campaignSendRepository.existsByCampaignIdAndRecipientRef(campaignId, recipient.id())) {
        continue; // BR4: already sent to this recipient for this campaign — idempotent skip
      }
      NewsletterSendResult sent =
          newsletterSender.send(new NewsletterMessage(campaignId, recipient.id(), campaign.code()));
      campaignSendRepository.save(
          CampaignSend.record(campaignId, recipient.id(), sent.providerMessageRef(), now));
      queued++;
    }

    campaign.markSent(now, actor);
    campaignRepository.save(campaign);
    events.publishEvent(new CampaignSent(campaignId, targeted, suppressed, now));
    log.info(
        "CampaignSent campaignId={} targeted={} suppressedNoConsent={} queued={} sentBy={}",
        campaignId,
        targeted,
        suppressed,
        queued,
        actor);
    return new CampaignSendResult(campaignId, targeted, suppressed, queued);
  }

  /** Fetches a campaign by id. */
  @Transactional(readOnly = true)
  public CampaignView getCampaign(UUID campaignId) {
    return campaignRepository
        .findById(campaignId)
        .map(Campaign::toView)
        .orElseThrow(CampaignNotFoundException::new);
  }

  // --- Attribution (BR5/DL-0057) ---

  /**
   * Registers a campaign→booking attribution intake (BR5/DL-0057): the carrier of the campaign code
   * declares the link. Idempotent per {@code (campaignCode, bookingId)} — re-registering returns
   * the existing row. The conversion is only confirmed later when the booking is confirmed.
   *
   * @param command the intake (campaign code + booking)
   * @param actor who registers it (audit)
   * @return the attribution view
   */
  @Transactional
  public AttributionView registerAttribution(RegisterAttributionCommand command, String actor) {
    return attributionRepository
        .findByCampaignCodeAndBookingId(command.campaignCode(), command.bookingId())
        .map(Attribution::toView)
        .orElseGet(
            () -> {
              Attribution attribution =
                  Attribution.register(
                      command.campaignCode(), command.bookingId(), clock.instant());
              attributionRepository.save(attribution);
              log.info(
                  "AttributionRegistered campaignCode={} bookingId={} by={}",
                  command.campaignCode(),
                  command.bookingId(),
                  actor);
              return attribution.toView();
            });
  }

  /**
   * Confirms the conversion of any attribution pre-registered for a confirmed booking
   * (BR5/DL-0057): called by the {@code BookingConfirmed} consumer. For each attribution of the
   * booking not yet converted, it flips it and publishes a {@link CampaignConverted} for the
   * Intelligence — <strong>idempotently</strong> (a re-delivered/duplicate confirmation publishes
   * nothing). A booking with no pre-registered code does nothing (no forced attribution).
   *
   * @param bookingId the confirmed booking (value)
   */
  @Transactional
  public void confirmConversion(UUID bookingId) {
    Instant now = clock.instant();
    for (Attribution attribution : attributionRepository.findByBookingId(bookingId)) {
      if (attribution.confirmConversion(now)) {
        attributionRepository.save(attribution);
        campaignRepository
            .findByCode(attribution.campaignCode())
            .ifPresent(
                campaign -> {
                  events.publishEvent(new CampaignConverted(campaign.id(), bookingId, now));
                  log.info(
                      "CampaignConverted campaignId={} bookingId={}", campaign.id(), bookingId);
                });
      }
    }
  }

  /** The attributions registered for a campaign code, newest first (BR5 report). */
  @Transactional(readOnly = true)
  public List<AttributionView> attributionsForCode(String campaignCode) {
    return attributionRepository.findByCampaignCodeOrderByAttributedAtDesc(campaignCode).stream()
        .map(Attribution::toView)
        .toList();
  }

  // --- LGPD erasure (BR6/DL-0058) ---

  /**
   * Attends an LGPD erasure request (BR6/DL-0058): removes the subject's marketing PII and ends
   * consent while <strong>preserving an anonymized revocation tombstone</strong> so the subject is
   * never silently re-included in a future send (the suppression the subject asked for). It first
   * appends a REVOKED row for every purpose the subject currently has GRANTED, then anonymizes
   * every consent row of the subject (clearing the free-text source and replacing the subject id
   * with an irreversible pseudonym). It never touches data another legal basis requires to keep
   * (fiscal in Compliance, financial entries, the booking) — those are outside this module. The
   * {@code attributions} (campaign code + booking, no subject PII) are business metrics and remain.
   *
   * @param subject the subject to erase (value)
   * @param actor who performs the erasure (audit)
   * @return the erasure result (how many rows anonymized; whether suppressed)
   */
  @Transactional
  public ErasureResult erase(SubjectRef subject, String actor) {
    Instant now = clock.instant();
    // 1) Ensure a revocation tombstone for any currently-granted purpose (suppression survives).
    // The seeded purpose set (MarketingCodes, DL-0116) replaces the old enum values() sweep; a
    // purpose added later as a cadastro item still gets anonymized by the id-based sweep in step 2.
    for (String purpose : MarketingCodes.KNOWN_PURPOSES) {
      if (currentState(subject, purpose).isGranted()) {
        Consent revocation =
            Consent.record(
                subject, purpose, LegalBasis.CONSENT, ConsentStatus.REVOKED, null, now, actor);
        consentRepository.save(revocation);
        events.publishEvent(new ConsentRevoked(subject.id(), subject.type(), purpose, now));
      }
    }
    // 2) Anonymize every consent row of the subject (PII removed; tombstone preserved).
    String pseudonym = pseudonymize(subject);
    List<Consent> rows = consentRepository.findAllForSubject(subject.type(), subject.id());
    for (Consent row : rows) {
      row.anonymize(pseudonym);
      consentRepository.save(row);
    }
    boolean suppressed = !rows.isEmpty();
    log.info(
        "MarketingErasure subjectType={} anonymizedConsents={} suppressed={} by={}",
        subject.type(),
        rows.size(),
        suppressed,
        actor);
    return new ErasureResult(pseudonym, rows.size(), suppressed);
  }

  // --- internals ---

  /**
   * An irreversible pseudonym for the subject (DL-0058): a SHA-256 hash of the type+id, so the
   * anonymized tombstone can no longer be tied back to the person while still being a stable,
   * collision-resistant key for the suppression record.
   */
  private static String pseudonymize(SubjectRef subject) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          digest.digest((subject.type() + ":" + subject.id()).getBytes(StandardCharsets.UTF_8));
      return "anon-" + HexFormat.of().formatHex(hash).substring(0, 32);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  /**
   * The candidate recipients for a purpose (DL-0059): the distinct subjects that have any consent
   * row for it — the base the module owns. Their current GRANTED/REVOKED status is resolved by the
   * send filter (BR2).
   */
  private List<SubjectRef> candidateRecipients(String purpose) {
    return consentRepository.findDistinctSubjectsForPurpose(purpose).stream()
        .map(row -> new SubjectRef((String) row[1], (String) row[0]))
        .toList();
  }

  /**
   * The subjects with a current GRANTED consent for a purpose (the reachable base, for preview).
   */
  private List<SubjectRef> consentedRecipients(String purpose) {
    return candidateRecipients(purpose).stream()
        .filter(recipient -> currentState(recipient, purpose).isGranted())
        .toList();
  }
}
