package com.fksoft.domain.commercialpolicy;

import java.util.UUID;

/**
 * The scope matcher of a governed parameter (SPEC-0014 BR1/BR3, DL-0037): a tuple of optional
 * dimensions (account, product, channel). A {@code null} dimension is a wildcard ("any").
 *
 * <p>The same record models both a <strong>rule's</strong> scope and a <strong>query's</strong>
 * scope:
 *
 * <ul>
 *   <li>A rule <em>matches</em> a query when every dimension the rule <strong>fixes</strong>
 *       (non-null) equals the query's value for that dimension (BR3). A wildcard dimension on the
 *       rule matches anything.
 *   <li>The rule's {@link #specificity()} is the number of fixed (non-null) dimensions — used to
 *       let the most specific rule win <em>within</em> a layer (product/account &gt; global, BR3).
 * </ul>
 *
 * No cross-context FK: {@code accountId}/{@code productRef}/{@code channel} are plain values copied
 * from the request (modules-and-apis.md).
 *
 * @param accountId the account this scope is bound to, or {@code null} for any account
 * @param productRef the product reference, or {@code null} for any product
 * @param channel the sales channel, or {@code null} for any channel
 */
public record ParameterScope(UUID accountId, String productRef, String channel) {

  private static final ParameterScope GLOBAL = new ParameterScope(null, null, null);

  public ParameterScope {
    productRef = blankToNull(productRef);
    channel = blankToNull(channel);
  }

  /** The global scope: no dimension fixed (specificity 0). */
  public static ParameterScope global() {
    return GLOBAL;
  }

  /** A scope bound only to an account. */
  public static ParameterScope ofAccount(UUID accountId) {
    return new ParameterScope(accountId, null, null);
  }

  /** The number of fixed (non-null) dimensions — the specificity used for intra-layer tie-break. */
  public int specificity() {
    int count = 0;
    if (accountId != null) {
      count++;
    }
    if (productRef != null) {
      count++;
    }
    if (channel != null) {
      count++;
    }
    return count;
  }

  /**
   * Whether this scope (read as a <strong>rule</strong> scope) matches the given {@code query}
   * scope (BR3): every dimension fixed here must equal the query's; a wildcard here matches
   * anything.
   *
   * @param query the resolution request scope
   * @return {@code true} when this rule scope applies to the query
   */
  public boolean matches(ParameterScope query) {
    return dimensionMatches(accountId, query.accountId)
        && dimensionMatches(productRef, query.productRef)
        && dimensionMatches(channel, query.channel);
  }

  private static <T> boolean dimensionMatches(T ruleValue, T queryValue) {
    return ruleValue == null || ruleValue.equals(queryValue);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
