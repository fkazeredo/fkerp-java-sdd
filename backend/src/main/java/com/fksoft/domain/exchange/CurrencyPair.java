package com.fksoft.domain.exchange;

import java.util.regex.Pattern;

/**
 * Value object for a currency pair such as {@code USD/BRL} (base/quote). Each side is a
 * three-letter uppercase currency code. The canonical text form uses a slash; parsing also accepts
 * a dash (handy in query strings, e.g. {@code USD-BRL}). The format invariant is enforced at
 * construction.
 *
 * @param base the base currency code (the one being priced, e.g. USD)
 * @param quote the quote currency code (the one it is priced in, e.g. BRL)
 */
public record CurrencyPair(String base, String quote) {

  private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

  public CurrencyPair {
    if (base == null
        || quote == null
        || !CODE.matcher(base).matches()
        || !CODE.matcher(quote).matches()) {
      throw new ExchangeCurrencyPairInvalidException();
    }
  }

  /**
   * Parses a textual pair using {@code /} or {@code -} as separator (e.g. {@code USD/BRL} or {@code
   * USD-BRL}), upper-casing the codes.
   *
   * @throws ExchangeCurrencyPairInvalidException when the text is not two three-letter codes.
   */
  public static CurrencyPair parse(String text) {
    if (text == null) {
      throw new ExchangeCurrencyPairInvalidException();
    }
    String[] parts = text.trim().toUpperCase().split("[/-]");
    if (parts.length != 2) {
      throw new ExchangeCurrencyPairInvalidException();
    }
    return new CurrencyPair(parts[0], parts[1]);
  }

  /** The canonical {@code BASE/QUOTE} text form. */
  public String asText() {
    return base + "/" + quote;
  }
}
