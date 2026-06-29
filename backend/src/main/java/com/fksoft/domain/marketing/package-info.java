/**
 * Marketing module (SPEC-0019): the B2B marketing context that governs <strong>consent (LGPD) as a
 * first-class citizen</strong>, segments the existing commercial base, runs campaigns through a
 * newsletter Anti-Corruption Layer, and attributes campaign→booking conversions for the DSS
 * (redesign Part 8.2-F). It is a Supporting context — explicitly <em>not</em> a CRM: a full CRM is
 * "buy" (this module is the consent/attribution layer, DL-0059).
 *
 * <p><strong>Consent</strong> ({@link com.fksoft.domain.marketing.internal.Consent}) is an
 * append-only log (DL-0056): every decision of a subject is an immutable row; the current state is
 * the most recent row per {@code (subjectType, subjectId, purpose)}; revoking or re-consenting is a
 * new row, never an update (BR1). No campaign send may reach a subject without a {@code GRANTED}
 * consent for that purpose (BR2) — the filter runs before enqueuing and the suppressed recipients
 * are counted, never a global error.
 *
 * <p><strong>Segment</strong> ({@link com.fksoft.domain.marketing.internal.Segment}) is defined by
 * criteria over already-existing data (minimization, BR3): the criteria are a validated jsonb
 * against a closed catalog of allowed fields (DL-0059). <strong>Campaign</strong> ({@link
 * com.fksoft.domain.marketing.internal.Campaign}) sends through the {@link
 * com.fksoft.domain.marketing.NewsletterSender} port (ACL, DL-0055; the provider DTO never crosses
 * into the domain) idempotently per {@code (campaignId, recipient)} (BR4).
 * <strong>Attribution</strong> ({@link com.fksoft.domain.marketing.internal.Attribution}) links a
 * campaign code to a booking (BR5/DL-0057): a code→booking intake plus a confirmation when {@link
 * com.fksoft.domain.booking.BookingConfirmed} arrives publishes {@link
 * com.fksoft.domain.marketing.CampaignConverted} for the Intelligence (SPEC-0013, consumer-leaf).
 *
 * <p>Spring Modulith application module. Dependencies: it consumes the {@code booking} {@code
 * BookingConfirmed} event only (never the Booking facade — DL-0057), the {@code newsletter} port it
 * owns, and the {@code money}/{@code error} kernels; no other business module depends back on
 * Marketing, so the module graph stays <strong>acyclic</strong> (Spring Modulith verify). The LGPD
 * erasure (BR6/DL-0058) removes marketing PII while preserving an anonymized revocation tombstone
 * so a subject is never silently re-included in a future send. The {@code internal} sub-package
 * (the aggregates and their repositories) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Marketing")
package com.fksoft.domain.marketing;
