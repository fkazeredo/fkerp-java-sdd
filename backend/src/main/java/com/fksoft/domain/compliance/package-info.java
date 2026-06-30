/**
 * Compliance module (SPEC-0008): the document vault that makes the supporting document a
 * first-class citizen. It stores documents with a content hash, optional signed format and a legal
 * retention deadline; links a document to a financial entry by value (no cross-module FK); knows,
 * per entry type, which document is mandatory; answers "can this period close?" (the veto consumed
 * by Finance, SPEC-0015); and blocks purge before the retention deadline (redesign 7.7; NBC ITG
 * 2000; CC arts. 1.179-1.180).
 *
 * <p>Spring Modulith application module. Types in this base package are the module's public API:
 * the {@link com.fksoft.domain.compliance.ComplianceService} use cases, value objects ({@link
 * com.fksoft.domain.compliance.DocumentType}, {@link com.fksoft.domain.compliance.RetentionPolicy},
 * {@link com.fksoft.domain.compliance.FileStorage} port), views, the {@link
 * com.fksoft.domain.compliance.DocumentAttached}/{@link
 * com.fksoft.domain.compliance.RequirementUnmet}/{@link
 * com.fksoft.domain.compliance.RetentionExpiring} events and the business exceptions. The
 * implementation types (entities, repositories, the Finance {@code CloseGuard} implementation) live
 * in this same package marked {@link com.fksoft.domain.ModuleInternal} and must never be reached
 * from other modules — encapsulation is enforced by ArchUnit (Phase 9 / ADR 0016), the module graph
 * stays acyclic (Spring Modulith verify).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Compliance")
package com.fksoft.domain.compliance;
