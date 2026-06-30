package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.billing.BillingInvoiceTransitionInvalidException;
import com.fksoft.domain.billing.CommissionInvoice;
import com.fksoft.domain.billing.InvoiceStatus;
import com.fksoft.domain.billing.TaxAssessment;
import com.fksoft.domain.billing.TaxRegime;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link CommissionInvoice} aggregate (SPEC-0016 BR1; DL-0045): the taxable base
 * is the commission (never the gross package), and the lifecycle RASCUNHO→EMITIDA→CANCELADA only
 * allows the legal transitions.
 */
class CommissionInvoiceTest {

  private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

  @Test
  void draftIsBornWithTheCommissionAsBaseNeverThePackage() {
    // Redesign 3.2: commission R$ 405 is the base; the package R$ 2.700 must never reach the
    // invoice.
    Money commission = Money.of(new BigDecimal("405.00"), "BRL");

    CommissionInvoice invoice =
        CommissionInvoice.draft(
            UUID.randomUUID(),
            commission,
            "3550308",
            "1.05",
            TaxRegime.SIMPLES_NACIONAL,
            NOW,
            "dev");

    assertThat(invoice.status()).isEqualTo(InvoiceStatus.RASCUNHO);
    assertThat(invoice.base()).isEqualTo(commission);
    // The aggregate has no field for the gross package — the base is the commission, period (BR1).
  }

  @Test
  void issueMovesDraftToIssuedWithNumberAndTax() {
    CommissionInvoice invoice =
        CommissionInvoice.draft(
            UUID.randomUUID(),
            Money.of(new BigDecimal("405.00"), "BRL"),
            "9999999",
            "1.05",
            TaxRegime.SIMPLES_NACIONAL,
            NOW,
            "dev");
    TaxAssessment assessment =
        new TaxAssessment(
            Money.of(new BigDecimal("20.25"), "BRL"), List.of(), TaxRegime.SIMPLES_NACIONAL);

    invoice.markIssued("2026/000123", "ABC123", assessment, UUID.randomUUID(), NOW, "dev");

    assertThat(invoice.status()).isEqualTo(InvoiceStatus.EMITIDA);
    assertThat(invoice.number()).isEqualTo("2026/000123");
    assertThat(invoice.verificationCode()).isEqualTo("ABC123");
    assertThat(invoice.iss()).isEqualTo(Money.of(new BigDecimal("20.25"), "BRL"));
  }

  @Test
  void cannotIssueAnAlreadyIssuedInvoice() {
    CommissionInvoice invoice = issuedInvoice();

    assertThatThrownBy(
            () ->
                invoice.markIssued(
                    "2026/000999",
                    "ZZZ999",
                    new TaxAssessment(
                        Money.of(new BigDecimal("20.25"), "BRL"),
                        List.of(),
                        TaxRegime.SIMPLES_NACIONAL),
                    UUID.randomUUID(),
                    NOW,
                    "dev"))
        .isInstanceOf(BillingInvoiceTransitionInvalidException.class);
  }

  @Test
  void cancelMovesIssuedToCancelled() {
    CommissionInvoice invoice = issuedInvoice();

    invoice.cancel("erro de emissão", NOW, "dev");

    assertThat(invoice.status()).isEqualTo(InvoiceStatus.CANCELADA);
  }

  @Test
  void cannotCancelADraft() {
    CommissionInvoice draft =
        CommissionInvoice.draft(
            UUID.randomUUID(),
            Money.of(new BigDecimal("405.00"), "BRL"),
            "9999999",
            "1.05",
            TaxRegime.SIMPLES_NACIONAL,
            NOW,
            "dev");

    assertThatThrownBy(() -> draft.cancel("motivo", NOW, "dev"))
        .isInstanceOf(BillingInvoiceTransitionInvalidException.class);
  }

  private static CommissionInvoice issuedInvoice() {
    CommissionInvoice invoice =
        CommissionInvoice.draft(
            UUID.randomUUID(),
            Money.of(new BigDecimal("405.00"), "BRL"),
            "9999999",
            "1.05",
            TaxRegime.SIMPLES_NACIONAL,
            NOW,
            "dev");
    invoice.markIssued(
        "2026/000123",
        "ABC123",
        new TaxAssessment(
            Money.of(new BigDecimal("20.25"), "BRL"), List.of(), TaxRegime.SIMPLES_NACIONAL),
        UUID.randomUUID(),
        NOW,
        "dev");
    return invoice;
  }
}
