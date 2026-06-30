package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Journey aggregate (SPEC-0022 BR2/BR3; DL-0069/DL-0070): the processed journey of a collaborator
 * for a period — the operational worked minutes, the contracted minutes frozen for the period and
 * the time-bank balance. Idempotent by {@code (employeeId, period)} (UNIQUE): re-processing the
 * same period refreshes it in place. The {@code snapshotRef} is the operational snapshot consumed
 * (by value, never an FK, never a legal document — BR6). Module-internal.
 */
@Entity
@Table(name = "journeys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Journey {

  @Id private UUID id;

  private UUID employeeId;

  private String period;

  private UUID snapshotRef;

  private int workedMinutes;

  private int contractedMinutes;

  private int balanceMinutes;

  private Instant processedAt;

  /**
   * Records a freshly processed journey for an (employee, period).
   *
   * @param employeeId the collaborator id
   * @param period the period ({@code YYYY-MM})
   * @param snapshotRef the operational snapshot consumed (value)
   * @param workedMinutes the operational worked minutes
   * @param contractedMinutes the contracted minutes for the period
   * @param balanceMinutes the time-bank balance ({@code worked - contracted})
   * @param now the processing instant (UTC)
   * @return a new, persistable journey
   */
  public static Journey record(
      UUID employeeId,
      String period,
      UUID snapshotRef,
      int workedMinutes,
      int contractedMinutes,
      int balanceMinutes,
      Instant now) {
    Journey journey = new Journey();
    journey.id = UUID.randomUUID();
    journey.employeeId = employeeId;
    journey.period = period;
    journey.snapshotRef = snapshotRef;
    journey.workedMinutes = workedMinutes;
    journey.contractedMinutes = contractedMinutes;
    journey.balanceMinutes = balanceMinutes;
    journey.processedAt = now;
    return journey;
  }

  /**
   * Refreshes an existing journey in place on a re-processing of the same (employee, period)
   * (idempotency, BR2): updates the snapshot consumed, the worked/contracted minutes, the balance
   * and the processing instant — never the identity.
   */
  public void refresh(
      UUID snapshotRef, int workedMinutes, int contractedMinutes, int balanceMinutes, Instant now) {
    this.snapshotRef = snapshotRef;
    this.workedMinutes = workedMinutes;
    this.contractedMinutes = contractedMinutes;
    this.balanceMinutes = balanceMinutes;
    this.processedAt = now;
  }

  /** The time-bank balance in minutes (positive = extras; negative = faltas). */
  public int balanceMinutes() {
    return balanceMinutes;
  }

  /** Projects to the public journey read view. */
  public JourneyView toView() {
    return new JourneyView(
        employeeId,
        period,
        TimeFormat.hhmm(workedMinutes),
        TimeFormat.hhmm(contractedMinutes),
        TimeFormat.signedHhmm(balanceMinutes),
        snapshotRef,
        processedAt);
  }
}
