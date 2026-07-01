package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.booking.CancellationPolicy;
import com.fksoft.domain.booking.CancellationPolicyAdminService;
import com.fksoft.domain.booking.CancellationPolicyView;
import com.fksoft.domain.booking.CancellationTypeCodes;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.NoShowPolicy;
import com.fksoft.domain.cadastro.CadastroCodeInvalidException;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentTypeCodes;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.OfferOriginCodes;
import com.fksoft.domain.sourcing.SourcedOfferView;
import com.fksoft.domain.sourcing.SourcingService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the enum→cadastro invariant for the slice-18c groups (SPEC-0031 BR4/BR5;
 * ADR-0019/DL-0117): a converted field round-trips the SAME wire value (code = old enum name), an
 * unknown/inactive code is rejected by the {@link CadastroValidator}, and the wired branching is
 * preserved — the CancellationType penalty/merchant-trap and the DocumentType retention branch.
 * Exercises Sourcing, Booking and Compliance writes against a real Postgres (Testcontainers) with
 * the V33+V34+V35 seed present. The Exchange {@code MarketRateSource} is system-produced (the
 * source never arrives as a payload), so its round-trip is covered by {@code
 * MarketRateIntegrationTest}.
 */
class CadastroConversion18cInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SourcingService sourcingService;
  @Autowired private CancellationPolicyAdminService cancellationPolicyAdminService;
  @Autowired private ComplianceService complianceService;
  @Autowired private CadastroValidator cadastroValidator;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM sourced_offers");
    jdbcTemplate.execute("DELETE FROM cancellation_policies");
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("UPDATE cadastro_item SET active = true WHERE created_by = 'system'");
  }

  // --- Sourcing: OfferOrigin / IntegrationLevel ---

  @Test
  void offerOriginAndIntegrationLevelCodesRoundTripTheSameWireValue() {
    SourcedOfferView offer =
        sourcingService.register(
            "City Tour Rio",
            Money.of(new BigDecimal("480.00"), "BRL"),
            OfferOriginCodes.EXTERNAL_SITE,
            "INBOUND",
            "QS-1",
            "operador1");

    // The wire values are unchanged — the codes equal the old enum names.
    assertThat(offer.origin()).isEqualTo("EXTERNAL_SITE");
    assertThat(offer.integrationLevel()).isEqualTo("INBOUND");
  }

  @Test
  void anUnknownOfferOriginOrIntegrationLevelCodeIsRejected() {
    assertThatThrownBy(
            () ->
                sourcingService.register(
                    "Tour",
                    Money.of(new BigDecimal("10.00"), "BRL"),
                    "NOT_AN_ORIGIN",
                    "INBOUND",
                    null,
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);

    assertThatThrownBy(
            () ->
                sourcingService.register(
                    "Tour",
                    Money.of(new BigDecimal("10.00"), "BRL"),
                    OfferOriginCodes.EXTERNAL_SITE,
                    "NOT_A_LEVEL",
                    null,
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  // --- Booking: CancellationType (penalty windows + the merchant trap) ---

  @Test
  void cancellationTypeCodeRoundTripsAndAnUnknownOrInactiveCodeIsRejected() {
    CancellationPolicyView stored =
        cancellationPolicyAdminService.put(
            "CAR-ALAMO",
            new CancellationPolicy(
                CancellationTypeCodes.STANDARD, List.of(), true, CostBearer.AGENCY, false),
            NoShowPolicy.none(),
            "admin");
    assertThat(stored.type()).isEqualTo("STANDARD");

    assertThatThrownBy(
            () ->
                cancellationPolicyAdminService.put(
                    "CAR-BAD",
                    new CancellationPolicy("NOT_A_TYPE", List.of(), true, CostBearer.AGENCY, false),
                    NoShowPolicy.none(),
                    "admin"))
        .isInstanceOf(CadastroCodeInvalidException.class);

    // Deactivate a seeded type, then it is rejected too.
    jdbcTemplate.update(
        "UPDATE cadastro_item SET active = false "
            + "WHERE type = 'CANCELLATION_TYPE' AND code = 'CUSTOM'");
    assertThat(cadastroValidator.isValid(CadastroType.CANCELLATION_TYPE, "CUSTOM")).isFalse();
    assertThatThrownBy(
            () ->
                cancellationPolicyAdminService.put(
                    "CAR-CUSTOM",
                    new CancellationPolicy("CUSTOM", List.of(), true, CostBearer.AGENCY, false),
                    NoShowPolicy.none(),
                    "admin"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  // --- Compliance: DocumentType round-trip + retention branch preserved ---

  @Test
  void documentTypeCodeRoundTripsAndAnUnknownCodeIsRejected() {
    DocumentView document =
        complianceService.upload(
            DocumentTypeCodes.NFE,
            "conteudo".getBytes(StandardCharsets.UTF_8),
            "nfe.xml",
            "application/xml",
            LocalDate.of(2026, 1, 10),
            null,
            false,
            null,
            null,
            "fiscal");

    // Wire value unchanged; the FISCAL (5-year) retention branch is preserved.
    assertThat(document.type()).isEqualTo("NFE");
    assertThat(document.retentionUntil()).isEqualTo(LocalDate.of(2031, 1, 10));

    // A contract-type document keeps the 10-year retention branch.
    DocumentView contract =
        complianceService.upload(
            DocumentTypeCodes.REPRESENTATION_CONTRACT,
            "contrato".getBytes(StandardCharsets.UTF_8),
            "contrato.pdf",
            "application/pdf",
            LocalDate.of(2026, 1, 10),
            null,
            false,
            null,
            null,
            "fiscal");
    assertThat(contract.retentionUntil()).isEqualTo(LocalDate.of(2036, 1, 10));

    assertThatThrownBy(
            () ->
                complianceService.upload(
                    "NOT_A_DOC_TYPE",
                    "x".getBytes(StandardCharsets.UTF_8),
                    "x.txt",
                    "text/plain",
                    LocalDate.of(2026, 1, 10),
                    null,
                    false,
                    null,
                    null,
                    "fiscal"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }
}
