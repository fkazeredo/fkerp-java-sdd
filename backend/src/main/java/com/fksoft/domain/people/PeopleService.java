package com.fksoft.domain.people;

import com.fksoft.domain.people.internal.Employee;
import com.fksoft.domain.people.internal.EmployeeRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the People HR side (SPEC-0022): owns the collaborator registry (BR1) and
 * — from slice 8i-2 — the period journey/time-bank processing over the operational snapshot
 * (BR2/BR3) and the discrepancy queue (BR4). It is separate from {@link PointSnapshotService} (the
 * operational collection/crawl-run owner from SPEC-0012): same module, distinct use cases. It never
 * treats the snapshot as a legal document (BR6) — the legal AFD/AEJ lives in the Compliance vault.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeopleService {

  private final EmployeeRepository employees;
  private final Clock clock;

  /**
   * Registers a collaborator (BR1), validating the contracted journey ({@code HH:mm}) and the
   * unique identifier (a duplicate is a translated business error, never a raw DB exception).
   *
   * @param command the create command
   * @param actor who registers it (audit)
   * @return the registered employee view
   * @throws EmployeeInvalidException when the data is invalid (BR1)
   * @throws EmployeeDuplicateException when the identifier is already in use (BR1)
   */
  @Transactional
  public EmployeeView register(CreateEmployeeCommand command, String actor) {
    if (command == null) {
      throw new EmployeeInvalidException();
    }
    ContractedJourney journey = ContractedJourney.parse(command.contractedJourney());
    if (employees.existsByIdentifier(safeTrim(command.identifier()))) {
      throw new EmployeeDuplicateException();
    }
    Employee employee =
        Employee.register(
            command.identifier(),
            command.admissionDate(),
            journey,
            command.contractDocumentId(),
            clock.instant(),
            actor);
    try {
      employees.save(employee);
    } catch (DataIntegrityViolationException duplicate) {
      // Concurrent registration of the same identifier (single-instance, ADR 0002 — rare).
      throw new EmployeeDuplicateException();
    }
    log.info(
        "EmployeeRegistered employeeId={} status={} contractedMinutes={}",
        employee.id(),
        employee.status(),
        employee.contractedMinutes());
    return employee.toView();
  }

  /**
   * Fetches a collaborator by id.
   *
   * @throws EmployeeNotFoundException when no collaborator has that id
   */
  @Transactional(readOnly = true)
  public EmployeeView getById(UUID id) {
    return employees.findById(id).map(Employee::toView).orElseThrow(EmployeeNotFoundException::new);
  }

  /** Lists collaborators, newest first, optionally filtered by status. */
  @Transactional(readOnly = true)
  public Page<EmployeeView> list(EmployeeStatus status, Pageable pageable) {
    Page<Employee> page =
        status == null
            ? employees.findAllByOrderByCreatedAtDesc(pageable)
            : employees.findByStatusOrderByCreatedAtDesc(status, pageable);
    return page.map(Employee::toView);
  }

  private static String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }
}
