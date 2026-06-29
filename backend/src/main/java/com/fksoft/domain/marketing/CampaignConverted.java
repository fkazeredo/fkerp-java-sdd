package com.fksoft.domain.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a campaign was attributed a conversion — a booking with a pre-registered campaign
 * code was confirmed (SPEC-0019 BR5/Events; DL-0057). Published in-process; consumed by the
 * Intelligence (SPEC-0013) to measure "what the campaign actually turned into sales" (redesign
 * 8.2-F). Carries the campaign id and the converted booking id (values) — no PII.
 *
 * @param campaignId the converted campaign id
 * @param bookingId the booking that converted (value)
 * @param occurredAt when the conversion was attributed
 */
public record CampaignConverted(UUID campaignId, UUID bookingId, Instant occurredAt) {}
