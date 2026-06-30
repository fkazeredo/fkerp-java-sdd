package com.fksoft.domain.booking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes the penalty windows of a cancellation policy to/from a compact, portable text form
 * ({@code hoursBefore:penaltyPct} pairs separated by {@code ;}, e.g. {@code "24:0.50;72:0.25"}).
 *
 * <p>The windows are stored as text rather than a JSON/jsonb column on purpose (Rule Zero): there
 * is no cross-database JSON query need here, so a small codec avoids pulling in a Hibernate JSON
 * type mapping just to persist a short list of pairs. Validation of each pair is delegated to
 * {@link PenaltyWindow}'s constructor, so a malformed stored value surfaces as the domain's
 * invalid-policy error rather than a raw parse exception.
 */
final class PenaltyWindowsCodec {

  private PenaltyWindowsCodec() {}

  /** Encodes the windows to the compact text form (empty string for no windows). */
  static String encode(List<PenaltyWindow> windows) {
    StringBuilder sb = new StringBuilder();
    for (PenaltyWindow w : windows) {
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append(w.hoursBefore()).append(':').append(w.penaltyPct().toPlainString());
    }
    return sb.toString();
  }

  /** Decodes the compact text form back into validated windows (empty/null ⇒ no windows). */
  static List<PenaltyWindow> decode(String encoded) {
    List<PenaltyWindow> windows = new ArrayList<>();
    if (encoded == null || encoded.isBlank()) {
      return windows;
    }
    for (String pair : encoded.split(";")) {
      String[] parts = pair.split(":");
      if (parts.length != 2) {
        throw new com.fksoft.domain.booking.CancellationPolicyInvalidException();
      }
      try {
        windows.add(
            new PenaltyWindow(Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim())));
      } catch (NumberFormatException badNumber) {
        throw new com.fksoft.domain.booking.CancellationPolicyInvalidException();
      }
    }
    return windows;
  }
}
