/**
 * Exchange module (SPEC-0003 + SPEC-0011): the director pins a single sell rate per currency pair
 * (the "frozen rate") served as an Open-Host Service (SPEC-0003); and, since SPEC-0011, the module
 * also owns the <em>market rate</em> (append-only time series via the {@link
 * com.fksoft.domain.exchange.MarketRateProvider} port + manual contingency registration) and the
 * <em>book position</em>: it opens an {@code FxPosition} when a confirmed sale carries a
 * foreign-currency cost, accrues the subsidy on opening, marks drift to market while open, and
 * closes on settlement — feeding the {@code LiveExposure} and {@code PromoFxResult} read-models
 * that decompose the sell gap into subsidy (intentional) × drift (risk).
 *
 * <p>Spring Modulith application module. The public API is the {@link
 * com.fksoft.domain.exchange.ExchangeRateService} and {@link
 * com.fksoft.domain.exchange.MarketRateService} use cases, the cross-module {@link
 * com.fksoft.domain.exchange.ExchangeRateProvider} and {@link
 * com.fksoft.domain.exchange.MarketRateProvider} ports, the {@link
 * com.fksoft.domain.exchange.CurrencyPair} value object, the views, the {@link
 * com.fksoft.domain.exchange.RatePinned} event and the business exceptions. The {@code internal}
 * sub-package (entities, repositories, event listeners) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Exchange")
package com.fksoft.domain.exchange;
