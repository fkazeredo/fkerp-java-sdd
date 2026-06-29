package com.fksoft.domain.marketing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of a campaign (SPEC-0019).
 *
 * @param id the campaign id
 * @param segmentId the targeted segment (value)
 * @param code the public attribution code
 * @param contentRef the pointer to the external creative, or {@code null}
 * @param windowFrom the start of the send window, or {@code null}
 * @param windowTo the end of the send window, or {@code null}
 * @param status DRAFT or SENT
 * @param createdAt when it was created
 */
public record CampaignView(
    UUID id,
    UUID segmentId,
    String code,
    String contentRef,
    LocalDate windowFrom,
    LocalDate windowTo,
    CampaignStatus status,
    Instant createdAt) {}
