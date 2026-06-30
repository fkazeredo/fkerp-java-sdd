package com.fksoft.domain.people;

import java.util.regex.Pattern;

/**
 * The contracted daily journey of a collaborator (SPEC-0022 BR1), as a value object: a positive
 * number of minutes per working day (e.g. {@code 480} = {@code 08:00}). It parses and validates the
 * {@code HH:mm} contract input and renders it back, so the invariant (a well-formed, positive,
 * within-a-day journey) lives in one place and is unit-testable.
 *
 * @param minutes the contracted minutes per day (1..1440)
 */
public record ContractedJourney(int minutes) {

  private static final Pattern HH_MM = Pattern.compile("^([0-9]{1,2}):([0-5][0-9])$");
  private static final int MINUTES_IN_A_DAY = 24 * 60;

  /** Validates the invariant: a positive journey that fits within a calendar day. */
  public ContractedJourney {
    if (minutes <= 0 || minutes > MINUTES_IN_A_DAY) {
      throw new EmployeeInvalidException();
    }
  }

  /**
   * Parses a {@code HH:mm} contracted-journey label into the value object (BR1).
   *
   * @param label the contracted journey as {@code HH:mm} (e.g. {@code "08:00"})
   * @return the parsed, validated value object
   * @throws EmployeeInvalidException when the label is missing or malformed
   */
  public static ContractedJourney parse(String label) {
    if (label == null) {
      throw new EmployeeInvalidException();
    }
    var matcher = HH_MM.matcher(label.trim());
    if (!matcher.matches()) {
      throw new EmployeeInvalidException();
    }
    int hours = Integer.parseInt(matcher.group(1));
    int mins = Integer.parseInt(matcher.group(2));
    return new ContractedJourney(hours * 60 + mins);
  }

  /** Renders the journey back as a {@code HH:mm} label (zero-padded). */
  public String toLabel() {
    return TimeFormat.hhmm(minutes);
  }
}
