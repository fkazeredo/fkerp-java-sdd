package com.fksoft.domain.exchange;

/**
 * Origin of a market-rate observation (SPEC-0011 BR1): {@link #FEED} comes from an external
 * provider through the {@link MarketRateProvider} port (future adapter, ACL); {@link #MANUAL} is a
 * contingency registration entered by an operator (the v1 path, DL-0025).
 */
public enum MarketRateSource {
  FEED,
  MANUAL
}
