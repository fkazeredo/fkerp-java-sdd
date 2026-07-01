package com.fksoft.domain.portfolio;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Portfolio module (SPEC-0020): the represented brands, their
 * representation contracts (with the controlled-clock expiry alert) and — from slice 8g-2 — the
 * brand goals with the realized-vs-goal projection over sales events.
 *
 * <p>Portfolio <strong>references</strong> the brand/contract; it never prices nor computes
 * commission (BR6), and it never vetoes a sale — selling a brand without an in-force contract only
 * surfaces an alert (DL-0061). Brand/supplier identifiers are not personal data, so no masking is
 * needed; business events are logged with the brandRef and the correlation id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

  private final RepresentedBrandRepository brandRepository;
  private final RepresentationContractRepository contractRepository;
  private final BrandGoalRepository goalRepository;
  private final BrandSaleAttributionRepository saleAttributionRepository;
  private final BrandRealizedRepository realizedRepository;
  private final CadastroValidator cadastroValidator;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Registers a represented brand (BR1), starting it ACTIVE. The {@code brandRef} is unique; a
   * duplicate surfaces as a translated business error, never a raw constraint leak
   * (persistence.md).
   *
   * @param command the brand details (brandRef, displayName)
   * @param actor who registers it (audit)
   * @return the persisted brand view
   * @throws BrandInvalidException when required data is missing (BR1)
   * @throws BrandDuplicateException when the brandRef already exists (BR1)
   */
  @Transactional
  public BrandView registerBrand(RegisterBrandCommand command, String actor) {
    if (command == null) {
      throw new BrandInvalidException();
    }
    String brandRef = command.brandRef() == null ? null : command.brandRef().trim();
    if (brandRef != null && brandRepository.existsByBrandRef(brandRef)) {
      throw new BrandDuplicateException();
    }
    Instant now = clock.instant();
    RepresentedBrand brand = RepresentedBrand.register(brandRef, command.displayName(), now, actor);
    try {
      brandRepository.saveAndFlush(brand);
    } catch (DataIntegrityViolationException duplicate) {
      throw new BrandDuplicateException();
    }
    events.publishEvent(new BrandRepresented(brand.brandRef(), now));
    log.info(
        "BrandRepresented brandId={} brandRef={} registeredBy={}",
        brand.id(),
        brand.brandRef(),
        actor);
    return brand.toView();
  }

  /**
   * Fetches a brand by id.
   *
   * @throws BrandNotFoundException when no brand has that id
   */
  @Transactional(readOnly = true)
  public BrandView getBrand(UUID id) {
    return brandRepository
        .findById(id)
        .map(RepresentedBrand::toView)
        .orElseThrow(BrandNotFoundException::new);
  }

  /** Lists brands, optionally filtered by status, newest first. */
  @Transactional(readOnly = true)
  public List<BrandView> listBrands(BrandStatus status) {
    List<RepresentedBrand> brands =
        status == null
            ? brandRepository.findAllByOrderByCreatedAtDesc()
            : brandRepository.findByStatusOrderByCreatedAtDesc(status);
    return brands.stream().map(RepresentedBrand::toView).toList();
  }

  /**
   * Deactivates a brand (BR5: audited). Idempotent.
   *
   * @param id the brand id
   * @param actor who performs it (audit)
   * @return the updated brand view
   * @throws BrandNotFoundException when no brand has that id
   */
  @Transactional
  public BrandView deactivateBrand(UUID id, String actor) {
    RepresentedBrand brand = brandRepository.findById(id).orElseThrow(BrandNotFoundException::new);
    brand.deactivate(clock.instant(), actor);
    brandRepository.save(brand);
    log.info("BrandDeactivated brandId={} brandRef={} by={}", brand.id(), brand.brandRef(), actor);
    return brand.toView();
  }

  /**
   * Registers a representation contract for a brand (BR2), linking the contract document already
   * stored in the Compliance vault by id (value). The brand must exist.
   *
   * @param brandRef the brand the contract covers
   * @param command the contract details
   * @param actor who registers it (audit)
   * @return the persisted contract view
   * @throws BrandNotFoundException when the brand does not exist
   * @throws RepresentationContractInvalidException when the validity window is invalid (BR2)
   */
  @Transactional
  public ContractView registerContract(
      String brandRef, RegisterContractCommand command, String actor) {
    brandRepository.findByBrandRef(brandRef).orElseThrow(BrandNotFoundException::new);
    Instant now = clock.instant();
    RepresentationContract contract =
        RepresentationContract.register(
            brandRef,
            command.validFrom(),
            command.validUntil(),
            command.documentId(),
            command.terms(),
            now,
            actor);
    contractRepository.save(contract);
    events.publishEvent(new RepresentationContractRegistered(brandRef, now));
    log.info(
        "RepresentationContractRegistered contractId={} brandRef={} validUntil={} by={}",
        contract.id(),
        brandRef,
        command.validUntil(),
        actor);
    return contract.toView();
  }

  /** The contracts of a brand, newest first (BR2 report). */
  @Transactional(readOnly = true)
  public List<ContractView> contractsForBrand(String brandRef) {
    return contractRepository.findByBrandRefOrderByCreatedAtDesc(brandRef).stream()
        .map(RepresentationContract::toView)
        .toList();
  }

  /**
   * Whether a brand has an in-force representation contract on a date (BR2/DL-0061): a read-model
   * answer that whoever composes a sale may consult to <strong>signal</strong> (not block) the
   * exception. The Portfolio never vetoes the sale.
   *
   * @param brandRef the brand (value)
   * @param on the date to evaluate (defaults to today when {@code null})
   * @return the coverage answer
   */
  @Transactional(readOnly = true)
  public ContractCoverage contractCoverage(String brandRef, LocalDate on) {
    LocalDate date = on != null ? on : LocalDate.now(clock);
    boolean covered =
        contractRepository.findByBrandRefOrderByCreatedAtDesc(brandRef).stream()
            .anyMatch(contract -> contract.isInForceOn(date));
    return new ContractCoverage(brandRef, date, covered);
  }

  /**
   * Sweeps contracts that are expiring and raises the governance alert once each (BR5/DL-0063). The
   * evaluation instant is a parameter (controlled clock, like {@code
   * AfterSalesService.markBreaches} and {@code BookingService.expirePendingBookings}), so the rule
   * is deterministically testable. A contract is "expiring" when its {@code validUntil} is within
   * {@value com.fksoft.domain.portfolio.RepresentationContract#EXPIRY_WARNING_DAYS} days of {@code
   * now} (or already past). Non-blocking; idempotent per contract.
   *
   * @param now the evaluation instant (UTC)
   * @return how many contracts were newly flagged as expiring
   */
  @Transactional
  public int flagExpiringContracts(Instant now) {
    LocalDate asOf = now.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate threshold = asOf.plusDays(30);
    List<RepresentationContract> candidates = contractRepository.findExpiringCandidates(threshold);
    int flagged = 0;
    for (RepresentationContract contract : candidates) {
      if (contract.signalExpiringIfDue(now, asOf)) {
        contractRepository.save(contract);
        events.publishEvent(
            new RepresentationExpiring(contract.brandRef(), contract.validUntil(), now));
        log.info(
            "RepresentationExpiring brandRef={} validUntil={} detectedAt={}",
            contract.brandRef(),
            contract.validUntil(),
            now);
        flagged++;
      }
    }
    return flagged;
  }

  // --- Goals + realized projection (BR3/BR4/DL-0062) ---

  /**
   * Defines a goal for a brand (BR3). The brand must exist; a goal is unique per (brand, period,
   * metric) — a duplicate surfaces as a translated business error, never a raw constraint leak.
   *
   * @param command the goal details (brand, period, metric, target)
   * @param actor who defines it (audit)
   * @return the persisted goal view
   * @throws BrandNotFoundException when the brand does not exist
   * @throws BrandGoalInvalidException when the data is missing/inconsistent or the goal is a
   *     duplicate (BR3)
   */
  @Transactional
  public GoalView defineGoal(DefineGoalCommand command, String actor) {
    if (command == null || command.brandRef() == null) {
      throw new BrandGoalInvalidException();
    }
    // Validate the goal-metric reference code against the cadastro (SPEC-0031 BR3/DL-0116) — an
    // unknown/inactive code is rejected (422) before any write.
    cadastroValidator.validate(CadastroType.GOAL_METRIC, command.metric());
    brandRepository.findByBrandRef(command.brandRef()).orElseThrow(BrandNotFoundException::new);
    if (goalRepository.existsByBrandRefAndPeriodAndMetric(
        command.brandRef(), command.period(), command.metric())) {
      throw new BrandGoalInvalidException();
    }
    Instant now = clock.instant();
    BrandGoal goal =
        BrandGoal.define(
            command.brandRef(),
            command.period(),
            command.metric(),
            command.targetAmount(),
            command.targetCount(),
            now,
            actor);
    try {
      goalRepository.saveAndFlush(goal);
    } catch (DataIntegrityViolationException duplicate) {
      throw new BrandGoalInvalidException();
    }
    log.info(
        "BrandGoalDefined goalId={} brandRef={} period={} metric={} by={}",
        goal.id(),
        goal.brandRef(),
        goal.period(),
        goal.metric(),
        actor);
    return goal.toView();
  }

  /**
   * Registers a sale→brand attribution intake (BR4/DL-0062): the carrier of the sale declares which
   * represented brand a booking belongs to, so the realized projection can group by brand without
   * changing the sale event. Idempotent per booking — re-registering returns the existing link. The
   * brand must exist.
   *
   * @param brandRef the represented brand (value)
   * @param bookingId the booking (value)
   * @param actor who registers it (audit)
   * @return the booking's attributed brand
   * @throws BrandNotFoundException when the brand does not exist
   */
  @Transactional
  public String attributeSale(String brandRef, UUID bookingId, String actor) {
    brandRepository.findByBrandRef(brandRef).orElseThrow(BrandNotFoundException::new);
    Optional<BrandSaleAttribution> existing = saleAttributionRepository.findByBookingId(bookingId);
    if (existing.isPresent()) {
      return existing.get().brandRef();
    }
    BrandSaleAttribution attribution =
        BrandSaleAttribution.register(bookingId, brandRef, clock.instant());
    saleAttributionRepository.save(attribution);
    log.info("BrandSaleAttributed brandRef={} bookingId={} by={}", brandRef, bookingId, actor);
    return brandRef;
  }

  /**
   * Projects a confirmed booking onto the brand's VOLUME realized (BR4/DL-0062): if the booking was
   * attributed to a brand, it adds a {@code +1} VOLUME contribution, idempotently per booking (the
   * {@code (VOLUME, bookingId)} key). A booking with no attribution does nothing (no forced
   * attribution). Called by the {@code BookingConfirmed} consumer.
   *
   * @param bookingId the confirmed booking (value)
   * @param occurredAt when it was confirmed
   */
  @Transactional
  public void recordSaleVolume(UUID bookingId, Instant occurredAt) {
    saleAttributionRepository
        .findByBookingId(bookingId)
        .ifPresent(
            attribution -> {
              String sourceRef = bookingId.toString();
              if (realizedRepository.existsByMetricAndSourceRef(
                  GoalMetricCodes.VOLUME, sourceRef)) {
                return; // idempotent: this booking was already counted
              }
              realizedRepository.save(
                  BrandRealized.volume(attribution.brandRef(), sourceRef, occurredAt));
              log.info(
                  "BrandVolumeRealized brandRef={} bookingId={}",
                  attribution.brandRef(),
                  bookingId);
            });
  }

  /**
   * Links a reconciliation case to its booking's attribution (DL-0062), so a later {@code
   * SpreadRealized} (which carries only the caseId) can be resolved to the brand. Idempotent.
   * Called by the {@code ReconciliationCaseOpened} consumer.
   *
   * @param caseId the reconciliation case (value)
   * @param bookingId the booking the case opened for (value)
   */
  @Transactional
  public void linkReconciliationCase(UUID caseId, UUID bookingId) {
    saleAttributionRepository
        .findByBookingId(bookingId)
        .ifPresent(
            attribution -> {
              attribution.linkCase(caseId);
              saleAttributionRepository.save(attribution);
            });
  }

  /**
   * Projects a realized spread onto the brand's REVENUE realized (BR4/DL-0062): if the case's
   * booking was attributed to a brand, it adds the realized spread (BRL) as a REVENUE contribution,
   * idempotently per case (the {@code (REVENUE, caseId)} key). A case with no attributed brand, or
   * a spread not in BRL, does nothing. Called by the {@code SpreadRealized} consumer.
   *
   * @param caseId the reconciliation case (value)
   * @param realizedSpread the realized spread (expected BRL — the Acme's revenue)
   * @param occurredAt when it was realized
   */
  @Transactional
  public void recordSaleRevenue(UUID caseId, Money realizedSpread, Instant occurredAt) {
    if (realizedSpread == null || !BrandGoal.REVENUE_CURRENCY.equals(realizedSpread.currency())) {
      return; // only BRL spread contributes to the BRL revenue goal (BR6: no FX here)
    }
    saleAttributionRepository
        .findByCaseId(caseId)
        .ifPresent(
            attribution -> {
              String sourceRef = caseId.toString();
              if (realizedRepository.existsByMetricAndSourceRef(
                  GoalMetricCodes.REVENUE, sourceRef)) {
                return; // idempotent: this case's spread was already counted
              }
              realizedRepository.save(
                  BrandRealized.revenue(
                      attribution.brandRef(), sourceRef, realizedSpread.amount(), occurredAt));
              log.info(
                  "BrandRevenueRealized brandRef={} caseId={} amount={}",
                  attribution.brandRef(),
                  caseId,
                  realizedSpread.amount());
            });
  }

  /**
   * The progress of a brand's goal for a period (BR4): the target vs the realized projected from
   * sales events, with the attainment percentage (scale 1, HALF_UP). The realized is aggregated
   * over the {@link BrandRealized} rows of the brand+metric whose {@code occurredAt} falls in the
   * period (computed in UTC: a {@code YYYY} period matches the whole year, a {@code YYYY-MM} the
   * month).
   *
   * @param brandId the brand id
   * @param period the period (YYYY or YYYY-MM)
   * @return the goal progress
   * @throws BrandNotFoundException when the brand does not exist
   * @throws BrandGoalInvalidException when no goal exists for that brand+period
   */
  @Transactional(readOnly = true)
  public GoalProgress goalProgress(UUID brandId, String period) {
    RepresentedBrand brand =
        brandRepository.findById(brandId).orElseThrow(BrandNotFoundException::new);
    List<BrandGoal> goals = goalRepository.findByBrandRefAndPeriod(brand.brandRef(), period);
    if (goals.isEmpty()) {
      throw new BrandGoalInvalidException();
    }
    BrandGoal goal =
        goals.get(0); // one goal per (brand, period, metric); first is enough per metric
    return progressFor(goal);
  }

  /** Builds the progress read-model for a goal (BR4). */
  private GoalProgress progressFor(BrandGoal goal) {
    if (GoalMetricCodes.REVENUE.equals(goal.metric())) {
      BigDecimal realized =
          realizedRepository
              .findByBrandRefAndMetric(goal.brandRef(), GoalMetricCodes.REVENUE)
              .stream()
              .filter(row -> matchesPeriod(row.occurredAt(), goal.period()))
              .map(BrandRealized::amount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      Money target = goal.targetMoney();
      Money realizedMoney = Money.of(realized, BrandGoal.REVENUE_CURRENCY);
      return new GoalProgress(
          goal.brandRef(),
          goal.period(),
          GoalMetricCodes.REVENUE,
          target,
          realizedMoney,
          null,
          null,
          attainment(realized, target.amount()));
    }
    int realizedCount =
        realizedRepository.findByBrandRefAndMetric(goal.brandRef(), GoalMetricCodes.VOLUME).stream()
            .filter(row -> matchesPeriod(row.occurredAt(), goal.period()))
            .mapToInt(BrandRealized::countInc)
            .sum();
    return new GoalProgress(
        goal.brandRef(),
        goal.period(),
        GoalMetricCodes.VOLUME,
        null,
        null,
        goal.targetCount(),
        realizedCount,
        attainment(BigDecimal.valueOf(realizedCount), BigDecimal.valueOf(goal.targetCount())));
  }

  /** The attainment percentage {@code realized / target * 100} (scale 1, HALF_UP). */
  private static BigDecimal attainment(BigDecimal realized, BigDecimal target) {
    if (target == null || target.signum() == 0) {
      return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    }
    return realized.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
  }

  /**
   * Whether an event instant falls in a goal period (UTC): a {@code YYYY} period matches the year,
   * a {@code YYYY-MM} period matches the month.
   */
  private static boolean matchesPeriod(Instant occurredAt, String period) {
    LocalDate date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
    if (period.length() == 4) {
      return String.valueOf(date.getYear()).equals(period);
    }
    return String.format("%04d-%02d", date.getYear(), date.getMonthValue()).equals(period);
  }
}
