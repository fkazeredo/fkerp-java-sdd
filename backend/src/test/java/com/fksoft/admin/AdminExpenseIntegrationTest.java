package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.admin.AdminExpenseDuplicateException;
import com.fksoft.domain.admin.AdminExpenseRegistered;
import com.fksoft.domain.admin.AdminExpenseView;
import com.fksoft.domain.admin.AdminService;
import com.fksoft.domain.admin.AdminSupplierView;
import com.fksoft.domain.admin.RegisterExpenseCommand;
import com.fksoft.domain.admin.RegisterSupplierCommand;
import com.fksoft.domain.compliance.CloseCheckView;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentType;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.finance.EntryStatus;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.money.Money;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for slice 8l-2 (SPEC-0025 BR3/BR4; V30; DL-0085/0086): registering a recurring
 * administrative expense creates the right PAYABLE entry in the Finance ledger and surfaces the
 * documents the Compliance requires; the mapping kind→entryType→document holds (UTILITY/SERVICE/
 * autonomous); registration is idempotent per (supplier, period, kind); and — the regression of the
 * golden rule — an administrative expense WITHOUT its document blocks the Finance period from
 * closing via the Compliance veto (fails before attaching, passes after). Runs against a real
 * Postgres (Testcontainers).
 */
@RecordApplicationEvents
class AdminExpenseIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AdminService adminService;
  @Autowired private FinanceService financeService;
  @Autowired private ComplianceService complianceService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;

  private static final String PERIOD = "2026-06";

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM admin_expenses");
    jdbcTemplate.execute("DELETE FROM admin_suppliers");
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  private AdminSupplierView supplier(String type) {
    return adminService.registerSupplier(
        new RegisterSupplierCommand(type, "61695227000193", "Fornecedor " + type), "admin");
  }

  @Test
  void registeringAUtilityExpenseCreatesThePayableEntryAndListsTheRequiredDocuments() {
    AdminSupplierView energy = supplier("UTILITY");

    AdminExpenseView expense =
        adminService.registerExpense(
            new RegisterExpenseCommand(
                energy.id(), PERIOD, Money.of(new BigDecimal("840.00"), "BRL"), "UTILITY"),
            "admin");

    // The Finance entry exists, PAYABLE, UTILITY_EXPENSE, PROVISIONAL, in the period (BR3/DL-0085).
    assertThat(expense.financeEntryId()).isNotNull();
    assertThat(expense.requiredDocuments()).containsExactly("UTILITY_BILL");
    var entry = financeService.getEntry(expense.financeEntryId());
    assertThat(entry.direction()).isEqualTo(LedgerDirection.PAYABLE);
    assertThat(entry.entryType().name()).isEqualTo("UTILITY_EXPENSE");
    assertThat(entry.status()).isEqualTo(EntryStatus.PROVISIONAL);
    assertThat(entry.period()).isEqualTo(PERIOD);

    // The event carries the created Finance entry id and the entry type (no personal data).
    assertThat(
            applicationEvents.stream(AdminExpenseRegistered.class)
                .filter(e -> e.expenseId().equals(expense.id()))
                .count())
        .isEqualTo(1);
  }

  @Test
  void serviceAndAutonomousMapToTheRightTypeAndDocuments() {
    AdminSupplierView software = supplier("SOFTWARE");
    AdminExpenseView service =
        adminService.registerExpense(
            new RegisterExpenseCommand(
                software.id(), PERIOD, Money.of(new BigDecimal("199.00"), "BRL"), "SERVICE"),
            "admin");
    assertThat(service.requiredDocuments()).containsExactly("NFSE");
    assertThat(financeService.getEntry(service.financeEntryId()).entryType().name())
        .isEqualTo("SERVICE");

    AdminSupplierView person = supplier("SERVICE");
    AdminExpenseView autonomous =
        adminService.registerExpense(
            new RegisterExpenseCommand(
                person.id(),
                PERIOD,
                Money.of(new BigDecimal("500.00"), "BRL"),
                "AUTONOMOUS_SERVICE"),
            "admin");
    assertThat(autonomous.requiredDocuments()).containsExactly("RPA");
    assertThat(financeService.getEntry(autonomous.financeEntryId()).entryType().name())
        .isEqualTo("AUTONOMOUS_SERVICE");
  }

  @Test
  void otherKindPostsWithNoMandatoryDocumentAtRegistration() {
    AdminSupplierView other = supplier("OTHER");
    AdminExpenseView expense =
        adminService.registerExpense(
            new RegisterExpenseCommand(
                other.id(), PERIOD, Money.of(new BigDecimal("75.00"), "BRL"), "OTHER"),
            "admin");
    assertThat(expense.requiredDocuments()).isEmpty();
    assertThat(financeService.getEntry(expense.financeEntryId()).entryType().name())
        .isEqualTo("OTHER_EXPENSE");
  }

  @Test
  void duplicateExpenseIsRejectedWithoutDoublePosting() {
    AdminSupplierView energy = supplier("UTILITY");
    adminService.registerExpense(
        new RegisterExpenseCommand(
            energy.id(), PERIOD, Money.of(new BigDecimal("840.00"), "BRL"), "UTILITY"),
        "admin");

    assertThatThrownBy(
            () ->
                adminService.registerExpense(
                    new RegisterExpenseCommand(
                        energy.id(), PERIOD, Money.of(new BigDecimal("840.00"), "BRL"), "UTILITY"),
                    "admin"))
        .isInstanceOf(AdminExpenseDuplicateException.class);

    // Exactly one ledger entry for this supplier — no double-post (DL-0086).
    Integer entries =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE party_id = ? AND entry_type = 'UTILITY_EXPENSE'",
            Integer.class,
            energy.id().toString());
    assertThat(entries).isEqualTo(1);
  }

  @Test
  void anAdministrativeExpenseWithoutItsDocumentBlocksTheMonthFromClosing() {
    // The golden rule (BR4 regression): the Compliance vetoes the close while the document is
    // missing.
    AdminSupplierView energy = supplier("UTILITY");
    AdminExpenseView expense =
        adminService.registerExpense(
            new RegisterExpenseCommand(
                energy.id(), PERIOD, Money.of(new BigDecimal("840.00"), "BRL"), "UTILITY"),
            "admin");

    // BEFORE attaching the bill: the period cannot close, the admin expense's entry is pending.
    CloseCheckView before = complianceService.closeCheck(PERIOD);
    assertThat(before.canClose()).isFalse();
    assertThat(before.pending())
        .anySatisfy(
            pending -> {
              assertThat(pending.entryId()).isEqualTo(expense.financeEntryId());
              assertThat(pending.missing()).contains("UTILITY_BILL");
            });

    // Attach the UTILITY_BILL to the Finance entry (Compliance vault).
    DocumentView bill =
        complianceService.upload(
            DocumentType.UTILITY_BILL,
            "conta-de-luz".getBytes(),
            "conta.pdf",
            "application/pdf",
            LocalDate.of(2026, 6, 20),
            null,
            false,
            expense.financeEntryId(),
            "UTILITY_EXPENSE",
            "admin");
    assertThat(bill).isNotNull();

    // AFTER attaching: the period can close (the veto cleared).
    CloseCheckView after = complianceService.closeCheck(PERIOD);
    assertThat(after.canClose()).isTrue();
    assertThat(after.pending()).isEmpty();
  }
}
