package com.fksoft.application.api.dto;

import java.util.UUID;

/**
 * Response for {@code GET /api/marketing/segments/{id}/preview} (SPEC-0019 BR3): the estimated
 * reach — the number of currently consented, reachable subjects (DL-0059).
 *
 * @param segmentId the segment id
 * @param reachable the number of consented, reachable subjects
 */
public record SegmentPreviewResponse(UUID segmentId, long reachable) {}
