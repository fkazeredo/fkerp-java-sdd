/**
 * Sourcing module (SPEC-0009): records <strong>where an offer comes from</strong> and its
 * integration level without deforming the domain (redesign Parte 3.3/6). Free text is a valid offer
 * ({@code SourcedOffer}) — no structured catalog required. It also owns the application side of the
 * first real Anti-Corruption Layer: it turns a translated inbound command (from the quotation-site
 * webhook) into a <strong>Quote INTEGRATED</strong> via the Quoting facade, idempotently per {@code
 * externalQuotationId}, never letting the external vendor DTO cross the boundary (BR6).
 *
 * <p>Spring Modulith application module. Types in this base package are the module's public API:
 * the {@link com.fksoft.domain.sourcing.SourcingService} use cases, the cross-boundary command
 * {@link com.fksoft.domain.sourcing.RegisterInboundQuotationCommand} (the only shape the ACL passes
 * in), the {@link com.fksoft.domain.sourcing.IntegrationFailureClass} classification, value
 * objects/enums, views, the {@link com.fksoft.domain.sourcing.OfferSourced}/{@link
 * com.fksoft.domain.sourcing.IntegratedQuoteCreated} events and the business exceptions. It
 * collaborates with Accounts and Quoting <strong>only through their public facades</strong>; the
 * {@code internal} sub-package (entities, repositories) is module-private (Spring Modulith verify).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Sourcing")
package com.fksoft.domain.sourcing;
