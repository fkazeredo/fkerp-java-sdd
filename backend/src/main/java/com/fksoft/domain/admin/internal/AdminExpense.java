package com.fksoft.domain.admin.internal;

import com.fksoft.domain.admin.AdminExpenseInvalidException;
import com.fksoft.domain.admin.AdminExpenseKind;
import com.fksoft.domain.admin.AdminExpenseView;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal recurring-administrative-expense aggregate root (SPEC-0025 BR3): one recorded recurring
 * cost (a month's utility bill, a subscription fee) of an administrative supplier, in a period,
 * that creates a PAYABLE entry in the Finance ledger (the {@code financeEntryId} kept by value,
 * never an FK). Idempotent per {@code (supplierId, period, kind)} (DL-0086) — enforced by a UNIQUE
 * index. Module-internal.
 */
@Entity
@Table(name = "admin_expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminExpense {

  private static final Pattern PERIOD = Pattern.compile("\\d{4}-\\d{2}");

  @Id private UUID id;

  private UUID supplierId;
  private String period;

  private BigDecimal amount;
  private String currency;

  @Enumerated(EnumType.STRING)
  private AdminExpenseKind kind;

  private UUID financeEntryId;

  private Instant createdAt;
  private String createdBy;

  /**
   * Registers a recurring administrative expense (BR3). Validates the mandatory data and the period
   * shape ({@code YYYY-MM}). The {@code financeEntryId} is set once the Finance ledger entry has
   * been created by the application service.
   *
   * @param supplierId the supplier the expense belongs to (required)
   * @param period the accounting period ({@code YYYY-MM}, required)
   * @param amount the expense amount (required)
   * @param kind the expense kind (required)
   * @param financeEntryId the created Finance ledger entry id (required — set by the service)
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable expense
   * @throws AdminExpenseInvalidException when a mandatory field is missing or the period is
   *     malformed
   */
  public static AdminExpense register(
      UUID supplierId,
      String period,
      Money amount,
      AdminExpenseKind kind,
      UUID financeEntryId,
      Instant now,
      String actor) {
    if (supplierId == null
        || period == null
        || !PERIOD.matcher(period).matches()
        || amount == null
        || kind == null
        || financeEntryId == null) {
      throw new AdminExpenseInvalidException();
    }
    AdminExpense expense = new AdminExpense();
    expense.id = UUID.randomUUID();
    expense.supplierId = supplierId;
    expense.period = period;
    expense.amount = amount.amount();
    expense.currency = amount.currency();
    expense.kind = kind;
    expense.financeEntryId = financeEntryId;
    expense.createdAt = now;
    expense.createdBy = actor;
    return expense;
  }

  /** The expense id. */
  public UUID id() {
    return id;
  }

  /** The created Finance ledger entry id (value). */
  public UUID financeEntryId() {
    return financeEntryId;
  }

  /**
   * Projects the aggregate to its public read view, carrying the document types required at
   * registration (from the Compliance, supplied by the service).
   *
   * @param requiredDocuments the document types the Compliance requires for the posted entry
   * @return the expense view
   */
  public AdminExpenseView toView(List<String> requiredDocuments) {
    return new AdminExpenseView(
        id,
        supplierId,
        period,
        Money.of(amount, currency),
        kind,
        financeEntryId,
        requiredDocuments,
        createdAt);
  }
}
