/**
 * Accounts module (SPEC-0002): the commercial account (agency or agent) that is the entry point of
 * every commercial operation. Owns the partner's commercial and legal identity; it does <strong>not
 * </strong> compute money (BR6).
 *
 * <p>Spring Modulith application module. Types in this base package are the module's public API
 * (the {@link com.fksoft.domain.accounts.AccountService} use cases, the cross-module {@link
 * com.fksoft.domain.accounts.AccountDirectory} port, value objects, the {@link
 * com.fksoft.domain.accounts.AccountRegistered} event and the business exceptions). The {@code
 * internal} sub-package (entity, repository) is module-private and must never be reached from other
 * modules (Spring Modulith verify).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Accounts")
package com.fksoft.domain.accounts;
