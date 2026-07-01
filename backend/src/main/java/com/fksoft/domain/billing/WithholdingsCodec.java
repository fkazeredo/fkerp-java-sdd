package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes the withholdings list to/from a compact text form ({@code KIND:amount} pairs, comma
 * separated, in the invoice's currency). Stored as text rather than a jsonb column on purpose (Rule
 * Zero, same posture as the booking penalty-windows and intelligence sources codecs): it is a short
 * list of stable kinds with no cross-database JSON query need. Empty for Simples Nacional
 * (DL-0044).
 */
final class WithholdingsCodec {

  private WithholdingsCodec() {}

  /** Encodes the withholdings to the compact text form (empty string for none). */
  static String encode(List<Withholding> withholdings) {
    if (withholdings == null || withholdings.isEmpty()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (Withholding withholding : withholdings) {
      parts.add(withholding.kind() + ":" + withholding.amount().amount().toPlainString());
    }
    return String.join(",", parts);
  }

  /** Decodes the compact text form back into a list, in the given currency (empty/null ⇒ empty). */
  static List<Withholding> decode(String encoded, String currency) {
    List<Withholding> withholdings = new ArrayList<>();
    if (encoded == null || encoded.isBlank()) {
      return withholdings;
    }
    for (String part : encoded.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int sep = trimmed.indexOf(':');
      String kind = trimmed.substring(0, sep);
      Money amount = Money.of(new BigDecimal(trimmed.substring(sep + 1)), currency);
      withholdings.add(new Withholding(kind, amount));
    }
    return withholdings;
  }
}
