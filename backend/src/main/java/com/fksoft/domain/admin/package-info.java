/**
 * Admin module (SPEC-0025): the administrative desk — a lean registry of administrative suppliers
 * (utilities, software/service PJ, self-employed) and their contracts, which feeds expense entries
 * into the Finance ledger and references the supporting documents in the Compliance vault. It is a
 * <strong>generic</strong> subdomain delivered as registry + seam (DL-0084): full procurement
 * (quotation/purchase order) is out of scope — buy it if required. Not to be confused with the
 * tourism brands/suppliers (Portfolio, SPEC-0020).
 *
 * <p>Spring Modulith application module (the 22nd business module). It <strong>orchestrates, it
 * does not reimplement</strong>: registering a recurring expense posts a PAYABLE entry through the
 * {@link com.fksoft.domain.finance.FinanceService} facade (the entry type mapped from the expense
 * kind, DL-0085) and reads the required documents through the {@link
 * com.fksoft.domain.compliance.DocumentRequirementDirectory} port — Admin only generates the entry
 * and references the document; it never imposes the document rule nor closes a period (BR4; the
 * veto stays Finance+Compliance). Every supplier/contract/expense change is audited via the
 * Platform's consolidated {@code system_audit} ({@link
 * com.fksoft.domain.platform.SystemAuditService}, BR6 / DL-0088). It references the Compliance
 * document and the Finance entry <strong>by value</strong> (no cross-module FK).
 *
 * <p>It depends on the {@code finance}/{@code compliance}/{@code platform} facades/ports and the
 * {@code money}/{@code error} kernels; none of those depend back on Admin, so the module graph
 * stays <strong>acyclic</strong> (Spring Modulith verify — DL-0086). Types in this base package are
 * the module's public API: the {@link com.fksoft.domain.admin.AdminService} use cases, the
 * commands/ views/value objects, the reference codes wired to behavior ({@link
 * com.fksoft.domain.admin.AdminExpenseCodes}, after the enum→cadastro conversion — SPEC-0031/
 * DL-0115), the events ({@link
 * com.fksoft.domain.admin.AdminSupplierRegistered}/{@link
 * com.fksoft.domain.admin.AdminContractRegistered}/{@link
 * com.fksoft.domain.admin.AdminExpenseRegistered}/{@link
 * com.fksoft.domain.admin.AdminContractExpiring}) and the business exceptions. The implementation
 * types (the aggregates and their repositories) live in this same package marked {@link
 * com.fksoft.domain.ModuleInternal} and must never be reached from other modules — encapsulation is
 * enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Admin")
package com.fksoft.domain.admin;
