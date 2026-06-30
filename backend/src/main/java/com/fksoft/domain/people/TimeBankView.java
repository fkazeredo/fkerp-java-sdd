package com.fksoft.domain.people;

/**
 * Public read view of the time-bank balance for a period (SPEC-0022 examples; DL-0070). Mirrors the
 * spec's example shape: worked/contracted as {@code HH:mm}, the signed balance and the count of
 * open discrepancies for the period.
 *
 * @param period the period ({@code YYYY-MM})
 * @param workedHours the worked time as {@code HH:mm}
 * @param contractedHours the contracted time as {@code HH:mm}
 * @param balance the balance as {@code ±HH:mm} (positive = extras; negative = faltas)
 * @param discrepancies how many open discrepancies the period has
 */
public record TimeBankView(
    String period, String workedHours, String contractedHours, String balance, int discrepancies) {}
