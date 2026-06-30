# Relatório — Fase 8l (Admin — Fornecedores e Contratos Administrativos · SPEC-0025)

> **Última sub-fase do bloco 8x.** Subdomínio **genérico** entregue como **registro enxuto + seam**.
> Release **0.20.0**, tag `0.20.0`.

## Fatias entregues

| Fatia | O que faz |
|---|---|
| **8l-1** | Módulo Modulith `admin` (22º). `AdminSupplier` (UTILITY/SOFTWARE/SERVICE/OTHER, nasce ACTIVE) + `AdminContract` (vigência/recorrência/valor/documento do Compliance por valor). `POST/GET /api/admin/suppliers` + `/suppliers/{id}/contracts`. Eventos `AdminSupplierRegistered`/`AdminContractRegistered`. **Escritas exigem ROLE_FINANCE** (403 auditado sem o papel); alteração auditada no `system_audit` (`ADMIN_CHANGE`, CNPJ/CPF mascarado). Migração V30. |
| **8l-2** | Despesa recorrente **cria** `LedgerEntry` PAYABLE no Finance via **fachada** `FinanceService.register` (sem FK) com o `entryType` mapeado do `kind` (DL-0085) e **lista os documentos exigidos** via nova **porta de leitura** `DocumentRequirementDirectory` (Compliance). Idempotente por `(supplier, period, kind)` (409 duplicada, sem 2º lançamento). **Regressão da regra de ouro**: despesa sem documento bloqueia o fechamento via Compliance. `EntryType` +SERVICE/+OTHER_EXPENSE e seed Compliance aditivos (V30, sem editar V8). |
| **8l-3** | `AdminContractExpiring` por **job de relógio controlado** (30d, idempotente por `expiry_signaled_at`). `POST /api/admin/contracts/flag-expiring` + `AdminContractExpiryScheduler` governado pelo Platform (`GovernedJobs` + job `admin-contract-expiry` seedado no catálogo, V30). **Alerta — nunca bloqueia.** |

## Arquivos criados/alterados (alto nível)

**Domínio (`com.fksoft.domain.admin`):** `AdminService`; agregados `internal/{AdminSupplier,
AdminContract, AdminExpense}` + repositórios; enums `AdminSupplierType`, `AdminSupplierStatus`,
`AdminRecurrence`, `AdminExpenseKind` (mapa kind→EntryType); comandos `RegisterSupplierCommand`/
`RegisterContractCommand`/`RegisterExpenseCommand`; views `AdminSupplierView`/`AdminContractView`/
`AdminExpenseView`; eventos `AdminSupplierRegistered`/`AdminContractRegistered`/
`AdminExpenseRegistered`/`AdminContractExpiring`; exceções (5); `package-info` `@ApplicationModule`.

**Delivery:** `AdminController`; DTOs `RegisterAdminSupplierRequest`/`RegisterAdminContractRequest`/
`RegisterAdminExpenseRequest`.

**Costuras em outros módulos (mínimas, aditivas):** Compliance — porta `DocumentRequirementDirectory`
+ implementação no `ComplianceService`; Finance — `EntryType` +SERVICE/+OTHER_EXPENSE; Platform —
`AuditType.ADMIN_CHANGE`; infra — `AdminContractExpiryScheduler` + `admin-contract-expiry` em
`GovernedJobs`; `SecurityConfig` (matchers POST `/api/admin/**` → FINANCE); `HttpErrorMapping` (5
exceções); i18n pt-BR + fallback en; `OpenApiConfig` (descrição + versão 0.20.0); `pom.xml` 0.20.0.

**Migração:** `V30__create_admin.sql` (idempotente) — `admin_suppliers`, `admin_contracts`
(+`expiry_signaled_at`), `admin_expenses` (UNIQUE `(supplier_id, period, kind)`); seed aditivo do
`SERVICE` em `document_requirements`; seed do job `admin-contract-expiry`. **A V8 não foi editada.**

## Specs/ADRs atualizados

- **SPEC-0025:** Open Questions (procurement; mapa kind→entryType) movidas para Business Rules como
  **ASSUMIDO** (BR7–BR11, ver DL-0084..0088).
- **ADRs:** nenhuma mudança arquitetural nova (segue ADR 0010/0011/0012/0014/0015 já vigentes).

## Migrações / OpenAPI

- Migração **V30** (uma; idempotente). OpenAPI **0.20.0**: novos endpoints `/api/admin/*`; erros novos
  `admin.supplier.not-found`/`admin.supplier.invalid`/`admin.contract.invalid`/`admin.expense.invalid`/
  `admin.expense.duplicate`.

## Testes por tipo e resultado

| Tipo | Casos novos | Onde |
|---|---|---|
| Unitário (domínio) | 6 | `AdminContractTest` (5), `AdminExpenseKindTest` (1) |
| Integração (Testcontainers/Postgres) | 16 | `AdminSupplierContractIntegrationTest` (8), `AdminExpenseIntegrationTest` (5), `AdminContractExpiryIntegrationTest` (3) |
| Arquitetura | (suíte global) | ArchUnit 15 regras + Spring Modulith (22º módulo acíclico) verdes |
| Regressão | incluída | despesa sem documento bloqueia o fechamento (8l-2); 403 sem ROLE_FINANCE (8l-1) |

**`cd backend && ./mvnw verify` → BUILD SUCCESS · 466 testes, 0 falhas** (eram 444 na 0.19.0; +22).
**0 Checkstyle violations**; Spotless 0 alterações; ArchUnit (15) e Modulith verdes; V30 aplicada.

## Decisões (decision-log) — links

- [DL-0084](decision-log/DL-0084-admin-new-module-lean-registry-no-procurement.md) — módulo próprio
  (22º), registro enxuto; **procurement = comprar** (Alta/Moderada).
- [DL-0085](decision-log/DL-0085-admin-expense-kind-to-entrytype-and-document-map.md) — mapa
  `kind`→`EntryType`→documento; `EntryType` +SERVICE/+OTHER_EXPENSE; seed Compliance aditivo
  (Média/Barata).
- [DL-0086](decision-log/DL-0086-admin-finance-compliance-integration-via-facades-acyclic.md) —
  integração por fachada/porta; idempotência por UNIQUE `(supplier, period, kind)`; folha, acíclico
  (Alta/Moderada).
- [DL-0087](decision-log/DL-0087-admin-contract-expiring-controlled-clock-alert.md) — alerta de
  contrato a vencer por relógio controlado (Média/Barata).
- [DL-0088](decision-log/DL-0088-admin-sensitive-endpoints-gated-by-role-finance-and-audited.md) —
  escritas exigem ROLE_FINANCE + auditoria (Média/Barata).

**Nenhuma** decisão é Confiança=Baixa **e** Reversibilidade=Cara: a Open Question de procurement foi
fechada pela própria spec; o resto segue padrões já validados.

## Riscos / o que fica para a próxima fase

- **Procurement completo** = comprar se exigido (DL-0084); o cadastro é a base + seam.
- **Confirmação/liquidação** da despesa e o `PAYMENT_PROOF` (AT_SETTLEMENT) são Finance/Payout.
- **Mapa kind→entryType→documento** (DL-0085, Média) confirmável com a contabilidade (só novo seed).
- **Tela Angular do Admin** fica para a Fase 10 (UX).
- **Fim do bloco 8x.** Próximas fases: 9 (achatar `internal`), 10 (UX/Frontend), 11 (observabilidade),
  12 (E2E/Playwright), 13 (IdP externo OIDC), 14 (upgrade Spring Boot 4), 15 (docs bilíngues — já em
  curso a cada fatia).
