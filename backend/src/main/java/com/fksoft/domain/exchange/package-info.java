/**
 * Exchange module (SPEC-0003): the director pins a single sell rate per currency pair (the "frozen
 * rate") and the module serves the prevailing rate to whoever composes a quote, as an Open-Host
 * Service. History is append-only and auditable.
 *
 * <p>Spring Modulith application module. The public API is the {@link
 * com.fksoft.domain.exchange.ExchangeRateService} use cases, the cross-module {@link
 * com.fksoft.domain.exchange.ExchangeRateProvider} Open-Host port (consumed in-process by Quoting,
 * SPEC-0005), the {@link com.fksoft.domain.exchange.CurrencyPair} value object, views, the {@link
 * com.fksoft.domain.exchange.RatePinned} event and the business exceptions. The {@code internal}
 * sub-package (entity, repository) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Exchange")
package com.fksoft.domain.exchange;
