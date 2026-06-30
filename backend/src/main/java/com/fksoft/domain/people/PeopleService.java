package com.fksoft.domain.people;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the People HR side (SPEC-0022): owns the collaborator registry (BR1), the
 * period journey/time-bank processing over the operational snapshot (BR2/BR3; DL-0069/DL-0070) and
 * the discrepancy queue (BR4; DL-0071). It is separate from {@link PointSnapshotService} (the
 * operational collection/crawl-run owner from SPEC-0012): same module, distinct use cases.
 *
 * <p>The snapshot is always treated as <strong>operational, non-legal</strong> data (BR6): the
 * service only reads its id to record the consumed {@code snapshotRef} by value and consumes
 * operational worked minutes/punch counts; it never archives a retention document here (the legal
 * AFD/AEJ lives in the Compliance vault). The journey computation is delegated to the pure {@link
 * JourneyCalculator}, so the date/time rules stay testable without infrastructure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeopleService {

  private static final Pattern PERIOD = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

  private final EmployeeRepository employees;
  private final JourneyRepository journeys;
  private final JourneyDiscrepancyRepository discrepancies;
  private final PointSnapshotRepository snapshots;
  private final Clock clock;
  private final ApplicationEventPublisher events;

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

  /**
   * Processes a collaborator period journey from the operational snapshot (BR2/BR3; DL-0069),
   * idempotently by {@code (employeeId, period)}: re-processing refreshes the journey in place. It
   * resolves the operational snapshot for the period/source (recording its id by value), runs the
   * pure {@link JourneyCalculator}, persists/refreshes the journey, raises discrepancy alerts
   * idempotently (BR4; DL-0071) and publishes {@link JourneyProcessed} (plus a {@link
   * JourneyDiscrepancy} per newly-raised alert).
   *
   * <p>The snapshot is consumed as <strong>non-legal operational</strong> data (BR6): no retention
   * document is created here.
   *
   * @param command the process command
   * @return the processed journey view
   * @throws EmployeeNotFoundException when the collaborator does not exist
   * @throws JourneyInvalidException when the command is malformed or no snapshot exists for the
   *     period
   */
  @Transactional
  public JourneyView processJourney(ProcessJourneyCommand command) {
    validate(command);
    Employee employee =
        employees.findById(command.employeeId()).orElseThrow(EmployeeNotFoundException::new);

    UUID snapshotRef =
        snapshots
            .findBySourceRefAndPeriodRef(command.sourceRef(), command.period())
            .map(PointSnapshot::id)
            .orElseThrow(JourneyInvalidException::new);

    int contractedPeriodMinutes = employee.contractedMinutes() * command.workingDays();
    JourneyComputation computation =
        JourneyCalculator.compute(
            command.workedMinutes(),
            contractedPeriodMinutes,
            command.expectedPunches(),
            command.actualPunches());

    Instant now = clock.instant();
    Journey journey =
        journeys
            .findByEmployeeIdAndPeriod(command.employeeId(), command.period())
            .map(
                existing -> {
                  existing.refresh(
                      snapshotRef,
                      computation.workedMinutes(),
                      computation.contractedMinutes(),
                      computation.balanceMinutes(),
                      now);
                  return existing;
                })
            .orElseGet(
                () ->
                    Journey.record(
                        command.employeeId(),
                        command.period(),
                        snapshotRef,
                        computation.workedMinutes(),
                        computation.contractedMinutes(),
                        computation.balanceMinutes(),
                        now));
    journeys.save(journey);

    raiseDiscrepancies(command.employeeId(), command.period(), computation, now);

    events.publishEvent(
        new JourneyProcessed(
            command.employeeId(), command.period(), computation.balanceMinutes(), now));
    log.info(
        "JourneyProcessed employeeId={} period={} workedMinutes={} balanceMinutes={} discrepancies={}",
        command.employeeId(),
        command.period(),
        computation.workedMinutes(),
        computation.balanceMinutes(),
        computation.discrepancies().size());
    return journey.toView();
  }

  /**
   * Reads a collaborator processed journey for a period.
   *
   * @throws JourneyNotFoundException when the journey was never processed
   */
  @Transactional(readOnly = true)
  public JourneyView getJourney(UUID employeeId, String period) {
    return journeys
        .findByEmployeeIdAndPeriod(employeeId, period)
        .map(Journey::toView)
        .orElseThrow(JourneyNotFoundException::new);
  }

  /**
   * Reads a collaborator time-bank for a period (saldo + open discrepancies).
   *
   * @throws JourneyNotFoundException when the journey was never processed
   */
  @Transactional(readOnly = true)
  public TimeBankView getTimeBank(UUID employeeId, String period) {
    JourneyView journey =
        journeys
            .findByEmployeeIdAndPeriod(employeeId, period)
            .map(Journey::toView)
            .orElseThrow(JourneyNotFoundException::new);
    int open =
        discrepancies.countByEmployeeIdAndPeriodAndStatus(
            employeeId, period, DiscrepancyStatus.OPEN);
    return new TimeBankView(
        period, journey.workedHours(), journey.contractedHours(), journey.balance(), open);
  }

  /** The discrepancy queue (BR4), newest first, filtered by period and/or status. */
  @Transactional(readOnly = true)
  public Page<DiscrepancyView> listDiscrepancies(
      String period, DiscrepancyStatus status, Pageable pageable) {
    boolean hasPeriod = period != null && !period.isBlank();
    Page<JourneyDiscrepancyRecord> page;
    if (hasPeriod && status != null) {
      page = discrepancies.findByPeriodAndStatusOrderByCreatedAtDesc(period, status, pageable);
    } else if (hasPeriod) {
      page = discrepancies.findByPeriodOrderByCreatedAtDesc(period, pageable);
    } else if (status != null) {
      page = discrepancies.findByStatusOrderByCreatedAtDesc(status, pageable);
    } else {
      page = discrepancies.findByOrderByCreatedAtDesc(pageable);
    }
    return page.map(JourneyDiscrepancyRecord::toView);
  }

  /**
   * Raises a discrepancy alert per detected kind, idempotently (DL-0071: unique per (employee,
   * period, kind)). It never auto-corrects (BR4). Publishes a {@link JourneyDiscrepancy} per
   * newly-raised alert (metric-equivalent journey_discrepancies_total).
   */
  private void raiseDiscrepancies(
      UUID employeeId, String period, JourneyComputation computation, Instant now) {
    for (DiscrepancyKind kind : computation.discrepancies()) {
      if (discrepancies.existsByEmployeeIdAndPeriodAndKind(employeeId, period, kind)) {
        continue;
      }
      try {
        discrepancies.save(JourneyDiscrepancyRecord.open(employeeId, period, kind, null, now));
      } catch (DataIntegrityViolationException alreadyRaised) {
        continue;
      }
      events.publishEvent(new JourneyDiscrepancy(employeeId, period, kind, now));
      log.info("JourneyDiscrepancy employeeId={} period={} kind={}", employeeId, period, kind);
    }
  }

  private static void validate(ProcessJourneyCommand command) {
    if (command == null
        || command.employeeId() == null
        || command.period() == null
        || !PERIOD.matcher(command.period()).matches()
        || isBlank(command.sourceRef())
        || command.workedMinutes() < 0
        || command.workingDays() < 0
        || command.expectedPunches() < 0
        || command.actualPunches() < 0) {
      throw new JourneyInvalidException();
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }
}
