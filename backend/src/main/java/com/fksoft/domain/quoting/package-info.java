/**
 * Quoting module (SPEC-0005, keystone): composes the suggested price of a MANUAL sale from base
 * price + frozen FX rate + two-sided commission + markup, persists {@code suggestedAmount} vs
 * {@code appliedAmount}, and records an {@code OverrideRecord} whenever a human diverges from the
 * suggestion. The system always computes and suggests; the human may diverge, but the divergence is
 * recorded against the suggestion (BR8), with the whole composition's provenance frozen (BR4).
 *
 * <p>The INTEGRATED branch (SPEC-0009) reuses the same aggregate: a trusted, closed external price
 * creates a quote with {@code priceOrigin = INTEGRATED} without running the suggestion engine and
 * without any override (the gateway is {@link com.fksoft.domain.quoting.QuoteIntegrationPort}, used
 * by the Sourcing ACL).
 *
 * <p>Spring Modulith application module. It collaborates with Accounts, Exchange, Commissioning and
 * CommercialPolicy <strong>only through their public facades/ports</strong> (never their
 * repositories). Public API: {@link com.fksoft.domain.quoting.QuoteService}, the cross-module
 * {@link com.fksoft.domain.quoting.QuoteDirectory} port (consumed by Booking and Reconciliation),
 * the {@link com.fksoft.domain.quoting.QuoteIntegrationPort} port (consumed by Sourcing), views,
 * the {@code QuoteComposed}/{@code PriceOverridden} events and the business exceptions. The
 * implementation types (entities, repository) live in this same package marked {@link
 * com.fksoft.domain.ModuleInternal} and must never be reached from other modules — encapsulation is
 * enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Quoting")
package com.fksoft.domain.quoting;
