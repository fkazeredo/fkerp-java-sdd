/**
 * Quoting module (SPEC-0005, keystone): composes the suggested price of a MANUAL sale from base
 * price + frozen FX rate + two-sided commission + markup, persists {@code suggestedAmount} vs
 * {@code appliedAmount}, and records an {@code OverrideRecord} whenever a human diverges from the
 * suggestion. The system always computes and suggests; the human may diverge, but the divergence is
 * recorded against the suggestion (BR8), with the whole composition's provenance frozen (BR4).
 *
 * <p>Spring Modulith application module. It collaborates with Accounts, Exchange, Commissioning and
 * CommercialPolicy <strong>only through their public facades/ports</strong> (never their
 * repositories). Public API: {@link com.fksoft.domain.quoting.QuoteService}, the cross-module
 * {@link com.fksoft.domain.quoting.QuoteDirectory} port (consumed by Booking and Reconciliation),
 * views, the {@code QuoteComposed}/{@code PriceOverridden} events and the business exceptions. The
 * {@code internal} sub-package (entities, repository) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Quoting")
package com.fksoft.domain.quoting;
