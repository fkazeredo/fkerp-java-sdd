package com.fksoft.domain.commercialpolicy;

import java.util.UUID;

/**
 * The scope a Quoting composition asks the markup for (SPEC-0014, DL-0040): the account and (when
 * known) the product reference and channel. A thin value object on the {@link MarkupProvider} port
 * so the Quoting module does not depend on the engine's internal {@link ParameterScope}. The engine
 * translates it to a {@link ParameterScope} when resolving {@code MARKUP_PCT}.
 *
 * @param accountId the account being quoted, or {@code null} for a global resolution
 * @param productRef the product reference, or {@code null} when not product-specific
 * @param channel the sales channel, or {@code null} when not channel-specific
 */
public record MarkupScope(UUID accountId, String productRef, String channel) {

  private static final MarkupScope GLOBAL = new MarkupScope(null, null, null);

  /** The global markup scope (no account/product/channel). */
  public static MarkupScope global() {
    return GLOBAL;
  }

  /** A markup scope bound to an account (the common Quoting case). */
  public static MarkupScope ofAccount(UUID accountId) {
    return new MarkupScope(accountId, null, null);
  }

  /** Translates this port-level scope to the engine's {@link ParameterScope}. */
  ParameterScope toParameterScope() {
    return new ParameterScope(accountId, productRef, channel);
  }
}
