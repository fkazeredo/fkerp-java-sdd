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
 * com.fksoft.domain.exchange.ExchangeRateService}, {@link
 * com.fksoft.domain.exchange.MarketRateService}, {@link
 * com.fksoft.domain.exchange.FxPositionService} and {@link
 * com.fksoft.domain.exchange.ExchangeExposureService} (the {@code LiveExposure} and {@code
 * PromoFxResult} read-models) use cases, the cross-module {@link
 * com.fksoft.domain.exchange.ExchangeRateProvider} and {@link
 * com.fksoft.domain.exchange.MarketRateProvider} ports, the {@link
 * com.fksoft.domain.exchange.CurrencyPair} value object, the views, the events ({@link
 * com.fksoft.domain.exchange.RatePinned}, {@link com.fksoft.domain.exchange.RateSubsidyAccrued},
 * {@link com.fksoft.domain.exchange.BookPositionDrifted}, {@link
 * com.fksoft.domain.exchange.FxPositionClosed}) and the business exceptions. The {@code internal}
 * sub-package (entities, repositories) is module-private.
 *
 * <p>The FX position is opened/closed by Reconciliation (SPEC-0007), which holds the frozen quote
 * provenance and calls {@link com.fksoft.domain.exchange.FxPositionService} with the foreign cost
 * and frozen rate. Exchange does not depend on {@code quoting}/{@code booking} (that would form a
 * dependency cycle); it owns only the subsidy/drift/gap math (DL-0028).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Exchange")
package com.fksoft.domain.exchange;
