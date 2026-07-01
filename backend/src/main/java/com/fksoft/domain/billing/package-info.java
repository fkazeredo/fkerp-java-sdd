/**
 * Billing module (SPEC-0016): issues the commission invoice (NFS-e de serviço) over the Acme's
 * commission — the real revenue — computing ISS and withholdings, transmitting to the municipal
 * webservice and archiving the fiscal document. The taxable <strong>base is the commission, never
 * the gross package</strong> (redesign 3.2/7.7; BR1, DL-0045): the aggregate has no field for the
 * supplier tariff that merely passes through.
 *
 * <p>Spring Modulith application module — a <strong>leaf</strong>: it depends only on the {@code
 * money}/{@code error} kernels and its own ports, never on Finance or Compliance (so the module
 * graph stays acyclic — Finance consumes the {@link
 * com.fksoft.domain.billing.CommissionInvoiceIssued} event instead, like {@code finance →
 * booking}). Types in this base package are the module's public API: the {@link
 * com.fksoft.domain.billing.BillingService} use cases, the swappable {@link
 * com.fksoft.domain.billing.TaxRegimeStrategy}/{@link
 * com.fksoft.domain.billing.MunicipalIssRateProvider}/{@link
 * com.fksoft.domain.billing.BillingTaxRegimeConfig} ports, the {@link
 * com.fksoft.domain.billing.NfseGateway}/{@link com.fksoft.domain.billing.CertificateSigner}
 * integration ports, value objects ({@link com.fksoft.domain.billing.TaxAssessment}, {@link
 * com.fksoft.domain.billing.Withholding}, the tax-regime codes {@link
 * com.fksoft.domain.billing.TaxRegimeCodes} — after the enum→cadastro conversion, SPEC-0031/
 * DL-0115), the views,
 * the {@link com.fksoft.domain.billing.CommissionInvoiceIssued}/{@link
 * com.fksoft.domain.billing.CommissionInvoiceCancelled} events and the business exceptions. The
 * implementation types (the aggregate, repositories, the Simples strategy, the rate provider) live
 * in this same package marked {@link com.fksoft.domain.ModuleInternal} and must never be reached
 * from other modules — encapsulation is enforced by ArchUnit (Phase 9 / ADR 0016), the module graph
 * stays acyclic (Spring Modulith verify).
 *
 * <p>Tax regime (SPEC-0016 Q7, DL-0044): the issuer regime is parametrized (Simples/Presumido/Real)
 * behind a swappable strategy; the default is Simples Nacional and the emitter is the Acme itself.
 * The accountant's real regime can be set later without refactoring. The municipal NFS-e webservice
 * is an external integration modeled as a domain port with a traceable mock in {@code
 * com.fksoft.infra.integration.nfse} (ACL — the vendor shape never crosses into the domain;
 * DL-0046).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing")
package com.fksoft.domain.billing;
