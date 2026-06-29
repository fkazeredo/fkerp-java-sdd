package com.fksoft.domain.marketing.internal;

import com.fksoft.domain.marketing.SegmentCriteria;
import com.fksoft.domain.marketing.SegmentView;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Segment aggregate root (SPEC-0019 BR3; DL-0059): a named set of criteria over already-existing
 * commercial data (minimization — no new personal data collected). The criteria are stored in the
 * {@code criteria_json} jsonb column, but they are always a <strong>validated</strong> {@link
 * SegmentCriteria} (closed catalog) — the jsonb is never a free-form bag. Module-internal.
 */
@Entity
@Table(name = "segments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Segment {

  @Id private UUID id;

  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  private String criteriaJson;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Defines a new segment from validated criteria (BR3/DL-0059).
   *
   * @param name the segment name
   * @param criteria the validated criteria
   * @param now the creation instant (UTC)
   * @param actor who creates it (audit)
   * @return a new, persistable segment
   */
  public static Segment define(String name, SegmentCriteria criteria, Instant now, String actor) {
    Segment segment = new Segment();
    segment.id = UUID.randomUUID();
    segment.name = name;
    segment.criteriaJson = SegmentCriteriaCodec.encode(criteria);
    segment.createdAt = now;
    segment.updatedAt = now;
    segment.createdBy = actor;
    segment.updatedBy = actor;
    return segment;
  }

  /** The segment id. */
  public UUID id() {
    return id;
  }

  /** The validated criteria (decoded from the jsonb column). */
  public SegmentCriteria criteria() {
    return SegmentCriteriaCodec.decode(criteriaJson);
  }

  /** Projects the aggregate to its public read view. */
  public SegmentView toView() {
    return new SegmentView(id, name, criteria().fields(), createdAt, updatedAt);
  }
}
