package com.fksoft.domain.people.internal;

import com.fksoft.domain.people.DiscrepancyKind;
import com.fksoft.domain.people.DiscrepancyStatus;
import com.fksoft.domain.people.DiscrepancyView;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A journey discrepancy in the treatment queue (SPEC-0022 BR4; DL-0071): an alert raised for an
 * odd/missing punch or an incoherent journal, awaiting human treatment. Unique per {@code
 * (employeeId, period, kind)} so re-processing the same period never duplicates an alert. The
 * entity is named with a {@code Record} suffix to avoid clashing with the public {@code
 * JourneyDiscrepancy} event; it is itself a JPA {@link Entity}, not a Java record. Module-internal.
 */
@Entity
@Table(name = "journey_discrepancies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyDiscrepancyRecord {

  @Id private UUID id;

  private UUID employeeId;

  private String period;

  @Enumerated(EnumType.STRING)
  private DiscrepancyKind kind;

  @Enumerated(EnumType.STRING)
  private DiscrepancyStatus status;

  private String detail;

  private Instant createdAt;
  private Instant resolvedAt;
  private String resolvedBy;

  /**
   * Opens a new discrepancy alert (BR4). Never auto-corrects — it only flags for human treatment.
   *
   * @param employeeId the collaborator id
   * @param period the period ({@code YYYY-MM})
   * @param kind the discrepancy kind
   * @param detail an optional human detail
   * @param now the detection instant (UTC)
   * @return a new, persistable OPEN discrepancy
   */
  public static JourneyDiscrepancyRecord open(
      UUID employeeId, String period, DiscrepancyKind kind, String detail, Instant now) {
    JourneyDiscrepancyRecord record = new JourneyDiscrepancyRecord();
    record.id = UUID.randomUUID();
    record.employeeId = employeeId;
    record.period = period;
    record.kind = kind;
    record.status = DiscrepancyStatus.OPEN;
    record.detail = detail;
    record.createdAt = now;
    return record;
  }

  /** Resolves the discrepancy (manual treatment), recording who/when — no recalculation (BR4). */
  public void resolve(Instant now, String actor) {
    this.status = DiscrepancyStatus.RESOLVED;
    this.resolvedAt = now;
    this.resolvedBy = actor;
  }

  /** Projects to the public discrepancy read view. */
  public DiscrepancyView toView() {
    return new DiscrepancyView(id, employeeId, period, kind, status, detail, createdAt);
  }
}
