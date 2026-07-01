package com.fksoft.domain.admin;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.compliance.DocumentRequirementDirectory;
import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.EntryType;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyType;
import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.SystemAuditService;
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
 * Application service for the Admin module (SPEC-0025): the administrative desk that registers
 * administrative suppliers (utilities, software/service PJ, self-employed) and their contracts, and
 * records recurring expenses that <strong>generate</strong> a PAYABLE entry in the Finance ledger
 * and <strong>reference</strong> the documents the Compliance requires.
 *
 * <p>Admin <strong>orchestrates, it does not reimplement</strong> (DL-0086): it posts the ledger
 * entry through the {@link FinanceService} facade and reads the required documents through the
 * {@link DocumentRequirementDirectory} port — it never imposes the document rule nor closes a
 * period (BR4); the veto stays Finance+Compliance. It is a <strong>leaf</strong> consumer: it
 * depends on those facades/ports plus the {@code money}/{@code error} kernels and the Platform
 * audit facade; none of them depend back on Admin, so the module graph stays acyclic (Spring
 * Modulith).
 *
 * <p>Every change to a supplier/contract/expense is audited via the consolidated {@code
 * system_audit} (BR6/DL-0088) — metadata only, with the supplier identifier <strong>masked</strong>
 * (it may be a self-employed's CPF — personal data).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

  private static final java.util.regex.Pattern PERIOD =
      java.util.regex.Pattern.compile("\\d{4}-\\d{2}");

  private final AdminSupplierRepository suppliers;
  private final AdminContractRepository contracts;
  private final AdminExpenseRepository expenses;
  private final FinanceService financeService;
  private final DocumentRequirementDirectory documentRequirements;
  private final CadastroValidator cadastroValidator;
  private final SystemAuditService systemAudit;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  // --- Suppliers (BR1) ---

  /**
   * Registers an administrative supplier (BR1), starting it ACTIVE, publishes {@link
   * AdminSupplierRegistered} and audits the change (BR6/DL-0088).
   *
   * @param command the supplier details
   * @param actor who registers it (audit)
   * @return the persisted supplier view
   * @throws AdminSupplierInvalidException when a mandatory field is missing (BR1)
   */
  @Transactional
  public AdminSupplierView registerSupplier(RegisterSupplierCommand command, String actor) {
    if (command == null) {
      throw new AdminSupplierInvalidException();
    }
    // Validate the supplier-type reference code against the cadastro (SPEC-0031 BR3/DL-0115) — an
    // unknown/inactive code is rejected (422) before any write.
    cadastroValidator.validate(CadastroType.ADMIN_SUPPLIER_TYPE, command.type());
    Instant now = clock.instant();
    AdminSupplier supplier =
        AdminSupplier.register(
            command.type(), command.identifier(), command.displayName(), now, actor);
    suppliers.save(supplier);
    events.publishEvent(new AdminSupplierRegistered(supplier.id().toString(), now));
    audit(actor, "SUPPLIER_REGISTERED", supplier.id(), "{\"type\":\"" + command.type() + "\"}");
    log.info(
        "AdminSupplierRegistered supplierId={} type={} by={}",
        supplier.id(),
        command.type(),
        actor);
    return supplier.toView();
  }

  /**
   * Fetches a supplier by id.
   *
   * @throws AdminSupplierNotFoundException when no supplier has that id
   */
  @Transactional(readOnly = true)
  public AdminSupplierView getSupplier(UUID id) {
    return suppliers
        .findById(id)
        .map(AdminSupplier::toView)
        .orElseThrow(AdminSupplierNotFoundException::new);
  }

  /**
   * Lists suppliers, optionally filtered by type and/or status, newest first (filters combinable).
   */
  @Transactional(readOnly = true)
  public List<AdminSupplierView> listSuppliers(String type, AdminSupplierStatus status) {
    List<AdminSupplier> result;
    if (type != null && status != null) {
      result = suppliers.findByTypeAndStatusOrderByCreatedAtDesc(type, status);
    } else if (type != null) {
      result = suppliers.findByTypeOrderByCreatedAtDesc(type);
    } else if (status != null) {
      result = suppliers.findByStatusOrderByCreatedAtDesc(status);
    } else {
      result = suppliers.findAllByOrderByCreatedAtDesc();
    }
    return result.stream().map(AdminSupplier::toView).toList();
  }

  // --- Contracts (BR2) ---

  /**
   * Registers an administrative contract for a supplier (BR2), linking the contract document
   * already stored in the Compliance vault by id (value). The supplier must exist. Publishes {@link
   * AdminContractRegistered} and audits the change (BR6/DL-0088).
   *
   * @param supplierId the supplier the contract covers
   * @param command the contract details
   * @param actor who registers it (audit)
   * @return the persisted contract view
   * @throws AdminSupplierNotFoundException when the supplier does not exist
   * @throws AdminContractInvalidException when the validity window is invalid (BR2)
   */
  @Transactional
  public AdminContractView registerContract(
      UUID supplierId, RegisterContractCommand command, String actor) {
    if (command == null) {
      throw new AdminContractInvalidException();
    }
    if (!suppliers.existsById(supplierId)) {
      throw new AdminSupplierNotFoundException();
    }
    // Recurrence is optional; when present it must be a valid, active cadastro code (SPEC-0031 BR3).
    if (command.recurrence() != null && !command.recurrence().isBlank()) {
      cadastroValidator.validate(CadastroType.ADMIN_RECURRENCE, command.recurrence());
    }
    Instant now = clock.instant();
    AdminContract contract =
        AdminContract.register(
            supplierId,
            command.validFrom(),
            command.validUntil(),
            command.recurrence(),
            command.amount(),
            command.documentId(),
            now,
            actor);
    contracts.save(contract);
    events.publishEvent(new AdminContractRegistered(supplierId.toString(), now));
    audit(
        actor,
        "CONTRACT_REGISTERED",
        contract.id(),
        "{\"supplierId\":\"" + supplierId + "\",\"validUntil\":\"" + command.validUntil() + "\"}");
    log.info(
        "AdminContractRegistered contractId={} supplierId={} validUntil={} by={}",
        contract.id(),
        supplierId,
        command.validUntil(),
        actor);
    return contract.toView();
  }

  /** The contracts of a supplier, newest first (BR2 report). */
  @Transactional(readOnly = true)
  public List<AdminContractView> contractsForSupplier(UUID supplierId) {
    if (!suppliers.existsById(supplierId)) {
      throw new AdminSupplierNotFoundException();
    }
    return contracts.findBySupplierIdOrderByCreatedAtDesc(supplierId).stream()
        .map(AdminContract::toView)
        .toList();
  }

  // --- Recurring expenses (BR3/BR4/DL-0085/DL-0086) ---

  /**
   * Registers a recurring administrative expense (BR3): creates a PAYABLE ledger entry in the
   * Finance with the entry type mapped from the kind (DL-0085), keeps the {@code financeEntryId} by
   * value, and surfaces the documents the Compliance requires for that entry (DL-0086). Idempotent
   * per {@code (supplier, period, kind)} — a duplicate is rejected without double-posting. Admin
   * does <strong>not</strong> impose the document rule nor close the period (BR4); it only
   * generates the entry and references the documents. Publishes {@link AdminExpenseRegistered} and
   * audits the change (BR6/DL-0088).
   *
   * @param command the expense details
   * @param actor who registers it (audit)
   * @return the persisted expense view (with the Finance entry id and required documents)
   * @throws AdminSupplierNotFoundException when the supplier does not exist
   * @throws AdminExpenseInvalidException when a mandatory field is missing (BR3)
   * @throws AdminExpenseDuplicateException when the same (supplier, period, kind) already exists
   */
  @Transactional
  public AdminExpenseView registerExpense(RegisterExpenseCommand command, String actor) {
    if (command == null
        || command.supplierId() == null
        || command.kind() == null
        || command.amount() == null
        || command.period() == null
        || !PERIOD.matcher(command.period()).matches()) {
      throw new AdminExpenseInvalidException();
    }
    if (!suppliers.existsById(command.supplierId())) {
      throw new AdminSupplierNotFoundException();
    }
    // Validate the expense-kind reference code against the cadastro (SPEC-0031 BR3/DL-0115).
    cadastroValidator.validate(CadastroType.ADMIN_EXPENSE_KIND, command.kind());
    if (expenses.existsBySupplierIdAndPeriodAndKind(
        command.supplierId(), command.period(), command.kind())) {
      throw new AdminExpenseDuplicateException();
    }

    // Map the kind code to the Finance entry type (DL-0085/DL-0115) — behavior preserved via the
    // AdminExpenseCodes constants; a new code with no wired mapping falls back to OTHER_EXPENSE.
    EntryType entryType = AdminExpenseCodes.entryTypeFor(command.kind());
    // Generate the AP ledger entry through the Finance facade (BR3/DL-0086) — never an FK.
    LedgerEntryView entry =
        financeService.register(
            LedgerDirection.PAYABLE,
            new Party(command.supplierId().toString(), PartyType.SUPPLIER),
            command.amount(),
            entryType,
            new AccountingPeriodId(command.period()),
            actor);

    Instant now = clock.instant();
    AdminExpense expense =
        AdminExpense.register(
            command.supplierId(),
            command.period(),
            command.amount(),
            command.kind(),
            entry.id(),
            now,
            actor);
    try {
      expenses.saveAndFlush(expense);
    } catch (DataIntegrityViolationException duplicate) {
      // A concurrent registration won the UNIQUE (supplier, period, kind) race — surface the
      // business error; the transaction rolls back the just-posted ledger entry too.
      throw new AdminExpenseDuplicateException();
    }

    List<String> requiredDocuments = documentRequirements.requiredAtRegistration(entryType.name());
    events.publishEvent(
        new AdminExpenseRegistered(expense.id(), entry.id(), entryType.name(), now));
    audit(
        actor,
        "EXPENSE_REGISTERED",
        expense.id(),
        "{\"financeEntryId\":\"" + entry.id() + "\",\"entryType\":\"" + entryType + "\"}");
    log.info(
        "AdminExpenseRegistered expenseId={} financeEntryId={} entryType={} period={} by={}",
        expense.id(),
        entry.id(),
        entryType,
        command.period(),
        actor);
    return expense.toView(requiredDocuments);
  }

  // --- Contract-expiry alert (BR5/DL-0087) ---

  /**
   * Sweeps administrative contracts that are expiring and raises {@link AdminContractExpiring} once
   * each (BR5/DL-0087). The evaluation instant is a parameter (controlled clock, like {@code
   * PortfolioService.flagExpiringContracts}), so the rule is deterministically testable. A contract
   * is "expiring" when its {@code validUntil} is within {@value
   * com.fksoft.domain.admin.AdminContract#EXPIRY_WARNING_DAYS} days of {@code now} (or already
   * past). Non-blocking; idempotent per contract.
   *
   * @param now the evaluation instant (UTC)
   * @return how many contracts were newly flagged as expiring
   */
  @Transactional
  public int flagExpiringContracts(Instant now) {
    LocalDate asOf = now.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate threshold = asOf.plusDays(AdminContract.EXPIRY_WARNING_DAYS);
    List<AdminContract> candidates = contracts.findExpiringCandidates(threshold);
    int flagged = 0;
    for (AdminContract contract : candidates) {
      if (contract.signalExpiringIfDue(now, asOf)) {
        contracts.save(contract);
        events.publishEvent(new AdminContractExpiring(contract.id(), contract.validUntil(), now));
        log.info(
            "AdminContractExpiring contractId={} validUntil={} detectedAt={}",
            contract.id(),
            contract.validUntil(),
            now);
        flagged++;
      }
    }
    return flagged;
  }

  /**
   * Records an administrative change in the consolidated system audit (BR6/DL-0088) — metadata
   * only, never a secret or a full personal identifier.
   */
  private void audit(String actor, String action, UUID resourceId, String detailJson) {
    systemAudit.record(
        AuditType.ADMIN_CHANGE,
        actor,
        "{\"action\":\""
            + action
            + "\",\"resourceId\":\""
            + resourceId
            + "\",\"detail\":"
            + detailJson
            + "}");
  }
}
