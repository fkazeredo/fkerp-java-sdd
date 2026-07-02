package com.fksoft.domain.exchange;

/**
 * The {@code MARKET_RATE_SOURCE} code constants the domain wires (SPEC-0031 BR5; DL-0117). After
 * {@code MarketRateSource} became an editable cadastro, the source is <strong>produced by the
 * system</strong>, never sent from the wire: the manual contingency endpoint records observations
 * as {@link #MANUAL} (the v1 path — DL-0025) and a future feed adapter records {@link #FEED}. There
 * is therefore no write-validation from a payload; these constants keep the two wired values stable
 * so a relabel of the cadastro item never changes what the contingency path records. The cadastro
 * owns the labels the screens render.
 */
public final class MarketRateSourceCodes {

  /** An external provider observation (future feed adapter, ACL). */
  public static final String FEED = "FEED";

  /** A contingency observation entered by an operator — the v1 path (DL-0025). */
  public static final String MANUAL = "MANUAL";

  private MarketRateSourceCodes() {}
}
