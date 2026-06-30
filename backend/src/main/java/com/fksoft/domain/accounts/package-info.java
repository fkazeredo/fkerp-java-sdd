/**
 * Accounts module (SPEC-0002): the commercial account (agency or agent) that is the entry point of
 * every commercial operation. Owns the partner's commercial and legal identity; it does <strong>not
 * </strong> compute money (BR6).
 *
 * <p>Spring Modulith application module. The module's public API is the {@link
 * com.fksoft.domain.accounts.AccountService} use cases, the cross-module {@link
 * com.fksoft.domain.accounts.AccountDirectory} port, value objects, the {@link
 * com.fksoft.domain.accounts.AccountRegistered} event and the business exceptions. The
 * implementation types (entity, repository) live in this same package marked {@link
 * com.fksoft.domain.ModuleInternal} and must never be reached from other modules — encapsulation is
 * enforced by ArchUnit (Phase 9 / ADR 0016), the module graph stays acyclic (Spring Modulith
 * verify).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Accounts")
package com.fksoft.domain.accounts;
