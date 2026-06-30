package com.fksoft.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.portfolio.BrandDuplicateException;
import com.fksoft.domain.portfolio.BrandNotFoundException;
import com.fksoft.domain.portfolio.BrandStatus;
import com.fksoft.domain.portfolio.BrandView;
import com.fksoft.domain.portfolio.ContractCoverage;
import com.fksoft.domain.portfolio.ContractView;
import com.fksoft.domain.portfolio.PortfolioService;
import com.fksoft.domain.portfolio.RegisterBrandCommand;
import com.fksoft.domain.portfolio.RegisterContractCommand;
import com.fksoft.domain.portfolio.RepresentationContractRegistered;
import com.fksoft.domain.portfolio.RepresentationExpiring;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for slice 8g-1 (SPEC-0020 BR1/BR2/BR5; V25; DL-0061/DL-0063): registering a
 * brand (unique brandRef → translated 409 on duplicate, never a raw constraint leak), fetching a
 * missing brand → 404, registering a representation contract that references a Compliance document
 * by value, the contract-coverage read-model (alert, not block) and the controlled-clock expiry
 * sweep that publishes {@code RepresentationExpiring} once. Runs against a real Postgres
 * (Testcontainers).
 */
@RecordApplicationEvents
class BrandAndContractIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PortfolioService portfolioService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM representation_contracts");
    jdbcTemplate.execute("DELETE FROM represented_brands");
  }

  private BrandView registerAlamo() {
    return portfolioService.registerBrand(
        new RegisterBrandCommand("ALAMO", "Alamo Rent a Car"), "admin");
  }

  @Test
  void registeringABrandStartsItActiveAndPublishesTheEvent() {
    BrandView brand = registerAlamo();

    assertThat(brand.status()).isEqualTo(BrandStatus.ACTIVE);
    assertThat(brand.brandRef()).isEqualTo("ALAMO");
    assertThat(portfolioService.getBrand(brand.id()).displayName()).isEqualTo("Alamo Rent a Car");
    assertThat(applicationEvents.stream(com.fksoft.domain.portfolio.BrandRepresented.class).count())
        .isEqualTo(1);
  }

  @Test
  void duplicateBrandRefIsATranslatedConflict() {
    registerAlamo();
    assertThatThrownBy(
            () ->
                portfolioService.registerBrand(
                    new RegisterBrandCommand("ALAMO", "Alamo again"), "admin"))
        .isInstanceOf(BrandDuplicateException.class);

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM represented_brands WHERE brand_ref = ?", Integer.class, "ALAMO");
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void fetchingAMissingBrandIsNotFound() {
    assertThatThrownBy(() -> portfolioService.getBrand(UUID.randomUUID()))
        .isInstanceOf(BrandNotFoundException.class);
  }

  @Test
  void listingFiltersByStatus() {
    BrandView active = registerAlamo();
    BrandView toDeactivate =
        portfolioService.registerBrand(new RegisterBrandCommand("HERTZ", "Hertz"), "admin");
    portfolioService.deactivateBrand(toDeactivate.id(), "admin");

    assertThat(portfolioService.listBrands(BrandStatus.ACTIVE))
        .extracting(BrandView::id)
        .contains(active.id());
    assertThat(portfolioService.listBrands(BrandStatus.INACTIVE))
        .extracting(BrandView::id)
        .containsExactly(toDeactivate.id());
  }

  @Test
  void registeringAContractLinksTheComplianceDocumentByValueAndPublishesTheEvent() {
    registerAlamo();
    UUID documentId = UUID.randomUUID();
    ContractView contract =
        portfolioService.registerContract(
            "ALAMO",
            new RegisterContractCommand(
                "ALAMO",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                documentId,
                Map.of("override", "15%")),
            "admin");

    assertThat(contract.documentId()).isEqualTo(documentId);
    assertThat(contract.terms()).containsEntry("override", "15%");
    assertThat(
            applicationEvents.stream(RepresentationContractRegistered.class)
                .filter(e -> e.brandRef().equals("ALAMO"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void registeringAContractForAMissingBrandIsNotFound() {
    assertThatThrownBy(
            () ->
                portfolioService.registerContract(
                    "GHOST",
                    new RegisterContractCommand(
                        "GHOST", LocalDate.of(2026, 1, 1), null, null, null),
                    "admin"))
        .isInstanceOf(BrandNotFoundException.class);
  }

  @Test
  void contractCoverageIsAReadModelAlertNotABlock() {
    registerAlamo();
    portfolioService.registerContract(
        "ALAMO",
        new RegisterContractCommand(
            "ALAMO", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null),
        "admin");

    ContractCoverage covered =
        portfolioService.contractCoverage("ALAMO", LocalDate.of(2026, 6, 29));
    assertThat(covered.covered()).isTrue();

    ContractCoverage notCovered =
        portfolioService.contractCoverage("ALAMO", LocalDate.of(2027, 6, 29));
    assertThat(notCovered.covered()).isFalse();

    // A brand with no contract at all is simply "not covered" — no exception (alert, not block).
    ContractCoverage noContract =
        portfolioService.contractCoverage("UNKNOWN", LocalDate.of(2026, 6, 29));
    assertThat(noContract.covered()).isFalse();
  }

  @Test
  void expirySweepPublishesRepresentationExpiringOnceForADueContract() {
    registerAlamo();
    LocalDate today = NOW.atZone(ZoneOffset.UTC).toLocalDate();
    // validUntil 10 days out → within the 30-day warning window.
    portfolioService.registerContract(
        "ALAMO",
        new RegisterContractCommand(
            "ALAMO", LocalDate.of(2026, 1, 1), today.plusDays(10), null, null),
        "admin");

    int flagged = portfolioService.flagExpiringContracts(NOW);
    assertThat(flagged).isEqualTo(1);
    assertThat(
            applicationEvents.stream(RepresentationExpiring.class)
                .filter(e -> e.brandRef().equals("ALAMO"))
                .count())
        .isEqualTo(1);

    // Idempotent: a second sweep does not re-publish.
    assertThat(portfolioService.flagExpiringContracts(NOW.plusSeconds(60))).isZero();
  }

  @Test
  void expirySweepIgnoresContractsFarFromExpiry() {
    registerAlamo();
    LocalDate today = NOW.atZone(ZoneOffset.UTC).toLocalDate();
    portfolioService.registerContract(
        "ALAMO",
        new RegisterContractCommand(
            "ALAMO", LocalDate.of(2026, 1, 1), today.plusDays(90), null, null),
        "admin");

    assertThat(portfolioService.flagExpiringContracts(NOW)).isZero();
  }
}
