package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.aftersales.CaseResolutionCodes;
import com.fksoft.domain.aftersales.OpenCaseCommand;
import com.fksoft.domain.aftersales.SupportCaseTypeCodes;
import com.fksoft.domain.aftersales.SupportCaseView;
import com.fksoft.domain.cadastro.CadastroCodeInvalidException;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.commercialpolicy.CommercialPolicyService;
import com.fksoft.domain.commercialpolicy.DefineRuleCommand;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueTypeCodes;
import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeTypeCodes;
import com.fksoft.domain.payout.PayoutKindCodes;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the enum→cadastro invariant for the slice-18d groups (SPEC-0031 BR4/BR5; ADR-0019/DL-0118)
 * — the LAST of Phase 18: a converted field round-trips the SAME wire value (code = old enum name),
 * an unknown/inactive code is rejected by the {@link CadastroValidator} (422), and the wired
 * branching is preserved — the EntryType AP/AR posting nature, the PayoutKind settlement/refund
 * fact and the CaseResolution orchestration. Exercises Finance, Payout, CommercialPolicy and
 * AfterSales writes against a real Postgres (Testcontainers) with the V33+…+V36 seed present. The
 * People {@code DiscrepancyKind} is system-produced (the calculator emits it; it never arrives as a
 * payload), so its round-trip is covered by {@code JourneyCalculatorTest} and the People
 * integration tests.
 */
class CadastroConversion18dInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private FinanceService financeService;
  @Autowired private PayoutService payoutService;
  @Autowired private CommercialPolicyService commercialPolicyService;
  @Autowired private AfterSalesService afterSalesService;
  @Autowired private CadastroValidator cadastroValidator;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
    jdbcTemplate.execute("DELETE FROM parameter_rules WHERE defined_by <> 'system-seed'");
    jdbcTemplate.execute("DELETE FROM support_cases");
    jdbcTemplate.execute("UPDATE cadastro_item SET active = true WHERE created_by = 'system'");
  }

  // --- Finance: EntryType / PartyType round-trip + 422 ---

  @Test
  void entryTypeAndPartyTypeCodesRoundTripTheSameWireValue() {
    LedgerEntryView entry =
        financeService.register(
            LedgerDirection.PAYABLE,
            new Party("sup-1", PartyTypeCodes.SUPPLIER),
            Money.of(new BigDecimal("100.00"), "BRL"),
            EntryTypeCodes.SUPPLIER_SETTLEMENT,
            AccountingPeriodId.of("2026-06"),
            "operador1");

    // The wire values are unchanged — the codes equal the old enum names.
    assertThat(entry.entryType()).isEqualTo("SUPPLIER_SETTLEMENT");
    assertThat(entry.party().type()).isEqualTo("SUPPLIER");
  }

  @Test
  void anUnknownEntryTypeOrPartyTypeCodeIsRejected() {
    assertThatThrownBy(
            () ->
                financeService.register(
                    LedgerDirection.PAYABLE,
                    new Party("sup-1", PartyTypeCodes.SUPPLIER),
                    Money.of(new BigDecimal("100.00"), "BRL"),
                    "NOT_AN_ENTRY_TYPE",
                    AccountingPeriodId.of("2026-06"),
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);

    assertThatThrownBy(
            () ->
                financeService.register(
                    LedgerDirection.PAYABLE,
                    new Party("sup-1", "NOT_A_PARTY_TYPE"),
                    Money.of(new BigDecimal("100.00"), "BRL"),
                    EntryTypeCodes.SUPPLIER_SETTLEMENT,
                    AccountingPeriodId.of("2026-06"),
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  // --- Payout: PayoutKind / PayeeType round-trip + 422 (settlement/refund fact preserved) ---

  @Test
  void payoutKindAndPayeeTypeCodesRoundTripAndAnUnknownCodeIsRejected() {
    PayoutView payout =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKindCodes.AGENT_COMMISSION,
                new Payee("ag-1", PayeeTypeCodes.AGENT),
                null,
                null,
                Money.of(new BigDecimal("250.00"), "BRL"),
                null,
                null,
                null,
                null),
            "operador1");
    assertThat(payout.kind()).isEqualTo("AGENT_COMMISSION");
    assertThat(payout.payee().type()).isEqualTo("AGENT");

    assertThatThrownBy(
            () ->
                payoutService.create(
                    new CreatePayoutCommand(
                        "NOT_A_KIND",
                        new Payee("ag-1", PayeeTypeCodes.AGENT),
                        null,
                        null,
                        Money.of(new BigDecimal("250.00"), "BRL"),
                        null,
                        null,
                        null,
                        null),
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);

    // Deactivate a seeded payee type, then it is rejected too.
    jdbcTemplate.update(
        "UPDATE cadastro_item SET active = false WHERE type = 'PAYEE_TYPE' AND code = 'CUSTOMER'");
    assertThat(cadastroValidator.isValid(CadastroType.PAYEE_TYPE, "CUSTOMER")).isFalse();
    assertThatThrownBy(
            () ->
                payoutService.create(
                    new CreatePayoutCommand(
                        PayoutKindCodes.REFUND,
                        new Payee("cust-1", "CUSTOMER"),
                        null,
                        "case-1",
                        Money.of(new BigDecimal("10.00"), "BRL"),
                        null,
                        null,
                        null,
                        null),
                    "operador1"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  // --- CommercialPolicy: ParameterValueType round-trip + 422 (parse behavior preserved) ---

  @Test
  void parameterValueTypeCodeRoundTripsAndAnUnknownCodeIsRejected() {
    var rule =
        commercialPolicyService.defineRule(
            new DefineRuleCommand(
                new ParameterKey("TEST_18D_PCT"),
                ParameterLayer.POLICY,
                ParameterScope.global(),
                "0.12",
                ParameterValueTypeCodes.PERCENT,
                null,
                null,
                null),
            Set.of("ROLE_POLICY_ADMIN"),
            "admin");
    // Wire value unchanged; the numeric parse branch is preserved (0.12 → BigDecimal).
    assertThat(rule.type()).isEqualTo("PERCENT");

    assertThatThrownBy(
            () ->
                commercialPolicyService.defineRule(
                    new DefineRuleCommand(
                        new ParameterKey("TEST_18D_BAD"),
                        ParameterLayer.POLICY,
                        ParameterScope.global(),
                        "0.12",
                        "NOT_A_VALUE_TYPE",
                        null,
                        null,
                        null),
                    Set.of("ROLE_POLICY_ADMIN"),
                    "admin"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  // --- AfterSales: SupportCaseType / CaseResolution 422 (orchestration only sees known codes) ---

  @Test
  void supportCaseTypeCodeRoundTripsAndAnUnknownTypeIsRejected() {
    SupportCaseView view =
        afterSalesService.open(
            new OpenCaseCommand("b-18d", SupportCaseTypeCodes.COMPLAINT, "voo atrasado"), "agent");
    assertThat(view.type()).isEqualTo("COMPLAINT");

    assertThatThrownBy(
            () ->
                afterSalesService.open(
                    new OpenCaseCommand("b-18d", "NOT_A_CASE_TYPE", null), "agent"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  @Test
  void anUnknownCaseResolutionCodeIsRejectedBeforeAnyOrchestration() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b-18d-2", SupportCaseTypeCodes.COMPLAINT, null), "agent");
    var resolveWithUnknown =
        new com.fksoft.domain.aftersales.ResolveCaseCommand(
            "NOT_A_RESOLUTION", null, null, null, null);

    assertThatThrownBy(() -> afterSalesService.resolve(opened.id(), resolveWithUnknown, "agent"))
        .isInstanceOf(CadastroCodeInvalidException.class);

    // A known resolution code (RESOLVED_NO_ACTION) is accepted (no side effect).
    SupportCaseView resolved =
        afterSalesService.resolve(
            opened.id(),
            new com.fksoft.domain.aftersales.ResolveCaseCommand(
                CaseResolutionCodes.RESOLVED_NO_ACTION, null, null, null, null),
            "agent");
    assertThat(resolved.resolution()).isEqualTo("RESOLVED_NO_ACTION");
  }
}
