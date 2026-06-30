package com.fksoft.domain.people.internal;

import com.fksoft.domain.people.ContractedJourney;
import com.fksoft.domain.people.EmployeeInvalidException;
import com.fksoft.domain.people.EmployeeStatus;
import com.fksoft.domain.people.EmployeeView;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Employee aggregate root (SPEC-0022 BR1): a collaborator the HR context maintains — a unique
 * business {@code identifier}, the admission date, the contracted daily journey (stored as minutes,
 * the {@link ContractedJourney} value object), the employment status ({@link EmployeeStatus}) and
 * an optional contract document referenced <strong>by value</strong> ({@code contractDocumentId} —
 * the Compliance vault holds the legal document, never an FK here, DL-0072). Module-internal.
 *
 * <p>The contracted journey is kept as a plain minute count column (the shape is small and known —
 * Rule Zero), reconstructed into the value object on read so the invariant is enforced in one
 * place.
 */
@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

  @Id private UUID id;

  private String identifier;

  private LocalDate admissionDate;

  private int contractedMinutes;

  @Enumerated(EnumType.STRING)
  private EmployeeStatus status;

  private UUID contractDocumentId;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new ACTIVE collaborator (BR1).
   *
   * @param identifier the unique business identifier (required)
   * @param admissionDate the admission date (required)
   * @param contractedJourney the contracted daily journey (required value object)
   * @param contractDocumentId the Compliance contract document id (value), or {@code null}
   * @param now the registration instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable ACTIVE employee
   * @throws EmployeeInvalidException when the identifier or admission date is missing (BR1)
   */
  public static Employee register(
      String identifier,
      LocalDate admissionDate,
      ContractedJourney contractedJourney,
      UUID contractDocumentId,
      Instant now,
      String actor) {
    if (identifier == null || identifier.isBlank() || admissionDate == null) {
      throw new EmployeeInvalidException();
    }
    Employee employee = new Employee();
    employee.id = UUID.randomUUID();
    employee.identifier = identifier.trim();
    employee.admissionDate = admissionDate;
    employee.contractedMinutes = contractedJourney.minutes();
    employee.status = EmployeeStatus.ACTIVE;
    employee.contractDocumentId = contractDocumentId;
    employee.createdAt = now;
    employee.updatedAt = now;
    employee.createdBy = actor;
    employee.updatedBy = actor;
    return employee;
  }

  /** The employee id. */
  public UUID id() {
    return id;
  }

  /** The contracted daily journey, reconstructed as the value object (invariant in one place). */
  public ContractedJourney contractedJourney() {
    return new ContractedJourney(contractedMinutes);
  }

  /** The contracted minutes per day (used by the journey calculation). */
  public int contractedMinutes() {
    return contractedMinutes;
  }

  /** Projects the aggregate to its public read view. */
  public EmployeeView toView() {
    return new EmployeeView(
        id, identifier, admissionDate, contractedJourney().toLabel(), status, contractDocumentId);
  }
}
