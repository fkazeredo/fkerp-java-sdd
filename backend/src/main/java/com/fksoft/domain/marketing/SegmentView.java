package com.fksoft.domain.marketing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Public read view of a segment (SPEC-0019). The criteria are exposed as the validated field map.
 *
 * @param id the segment id
 * @param name the segment name
 * @param criteria the validated criteria fields
 * @param createdAt when it was created
 * @param updatedAt when it was last updated
 */
public record SegmentView(
    UUID id, String name, Map<String, String> criteria, Instant createdAt, Instant updatedAt) {}
