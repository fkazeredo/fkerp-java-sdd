package com.fksoft.domain.people;

/**
 * Small formatting helper for journey/time-bank durations as {@code HH:mm} (SPEC-0022 examples). It
 * keeps the {@code HH:mm} rendering — unsigned for worked/contracted hours and signed for the
 * time-bank balance ({@code +00:20} / {@code -01:10}) — in one tested place. Not a value object:
 * pure formatting of an already-validated minute count.
 */
public final class TimeFormat {

  private TimeFormat() {}

  /** Formats a non-negative minute count as {@code HH:mm} (hours may exceed two digits). */
  public static String hhmm(int minutes) {
    int safe = Math.max(minutes, 0);
    return String.format("%02d:%02d", safe / 60, safe % 60);
  }

  /** Formats a signed minute count as {@code ±HH:mm} (the time-bank balance, DL-0070). */
  public static String signedHhmm(int minutes) {
    String sign = minutes < 0 ? "-" : "+";
    return sign + hhmm(Math.abs(minutes));
  }
}
