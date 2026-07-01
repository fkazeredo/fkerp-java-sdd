/**
 * Cadastro module (SPEC-0031; ADR-0019/DL-0115): the generic registry of <strong>editable reference
 * data</strong>. Business enums that are neither state machines nor law-fixed become rows in {@code
 * cadastro_item(type, code, label, active, sort_order, …)} — a code (= the old enum constant name),
 * a human pt-BR label, an active flag and an order. The owner can add codes and relabel/deactivate
 * them without a redeploy; converting an enum to a cadastro never changes the wire contract (the
 * persisted value stays the same string — DL-0115).
 *
 * <p>Spring Modulith application module (the 23rd business module) and a <strong>leaf</strong>: it
 * depends only on its own repository and the {@code error} kernel — no other business module. Other
 * modules validate a code through the public {@link com.fksoft.domain.cadastro.CadastroValidator}
 * port; the {@code code} crosses the boundary as a <strong>value</strong>, never a cross-context FK,
 * and the dependency direction is {@code caller → cadastro}, so the module graph stays
 * <strong>acyclic</strong> (Spring Modulith verify). Domain logic that branches on specific codes
 * keeps its own small {@code *Codes} constants in the owning module (DL-0115) — the cadastro owns the
 * extensible set + labels, not the wired behavior.
 *
 * <p>Types in this base package are the module's public API: the {@link
 * com.fksoft.domain.cadastro.CadastroService} use cases, the {@link
 * com.fksoft.domain.cadastro.CadastroValidator} port, the {@link
 * com.fksoft.domain.cadastro.CadastroType} catalogue, the commands/views and the business
 * exceptions. The {@link com.fksoft.domain.cadastro.CadastroItem} aggregate and its repository are
 * marked {@link com.fksoft.domain.ModuleInternal} and must never be reached from other modules —
 * encapsulation is enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cadastro")
package com.fksoft.domain.cadastro;
