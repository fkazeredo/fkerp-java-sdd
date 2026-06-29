package com.fksoft.domain.finance;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * A period identifier in {@code YYYY-MM} form (SPEC-0015): the calendar month a ledger entry
 * belongs to and the unit of the monthly close. Validates the format on construction.
 *
 * @param value the canonical {@code YYYY-MM} string
 */
public record AccountingPeriodId(String value) {

  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  public AccountingPeriodId {
    if (value == null) {
      throw new FinancePeriodInvalidException();
    }
    try {
      YearMonth.parse(value, FORMAT);
    } catch (DateTimeParseException invalid) {
      throw new FinancePeriodInvalidException();
    }
  }

  /**
   * Parses a {@code YYYY-MM} string into a period id (same validation as the canonical
   * constructor).
   */
  public static AccountingPeriodId of(String value) {
    return new AccountingPeriodId(value);
  }
}
