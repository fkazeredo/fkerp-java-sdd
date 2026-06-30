package com.fksoft.domain.portfolio;

import com.fksoft.domain.portfolio.internal.RepresentationContract;
import com.fksoft.domain.portfolio.internal.RepresentationContractRepository;
import com.fksoft.domain.portfolio.internal.RepresentedBrand;
import com.fksoft.domain.portfolio.internal.RepresentedBrandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
   * {@value com.fksoft.domain.portfolio.internal.RepresentationContract#EXPIRY_WARNING_DAYS} days
   * of {@code now} (or already past). Non-blocking; idempotent per contract.
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
}
