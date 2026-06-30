# Plano — Fase 8l: Admin (Fornecedores e Contratos Administrativos) · SPEC-0025

> O **balcão administrativo**: cadastro **enxuto** de fornecedores administrativos (luz, água,
> telefone, software/serviço PJ, autônomo) e seus **contratos**, que alimenta lançamentos no
> **Finance (SPEC-0015)** e referencia documentos no **Compliance (SPEC-0008)**, gateado por papel
> (**Identity**, SPEC-0024). **Não** é procurement completo (cotação/compra = comprar — DL-0084).
> **Última sub-fase do bloco 8x.** Convenção: prosa em pt-BR, código em inglês. Release alvo: **0.20.0**.

## Decisões já registradas (decision-log)

| DL | Decisão | Conf. | Rev. |
|---|---|---|---|
| DL-0084 | Admin é módulo Modulith próprio (22º), registro enxuto; procurement = comprar (fronteira) | Alta | Moderada |
| DL-0085 | Mapa `kind`→`EntryType`→documento; `EntryType` +SERVICE/+OTHER_EXPENSE (aditivo); seed Compliance aditivo | Média | Barata |
| DL-0086 | Posta lançamento por chamada síncrona à fachada `FinanceService.register`; idempotência por UNIQUE `(supplier, period, kind)`; documentos via porta `DocumentRequirementDirectory`; folha, acíclico | Alta | Moderada |
| DL-0087 | `AdminContractExpiring` por job de relógio controlado (30d, idempotente, alerta) | Média | Barata |
| DL-0088 | Escritas exigem ROLE_FINANCE (403 + auditoria); alteração auditada via `system_audit` (`ADMIN_CHANGE`) | Média | Barata |

## Fronteiras de arquitetura (inegociáveis)

- Novo módulo `com.fksoft.domain.admin` com `package-info.java` `@ApplicationModule` (22º módulo de
  negócio). Depende **só** das fachadas/portas públicas de outros módulos: `FinanceService`,
  `DocumentRequirementDirectory` (porta nova de leitura do Compliance), `SystemAuditService`
  (Platform) + kernels `money`/`error`. **Nenhum** depende de volta → grafo Modulith **acíclico**
  (verify verde).
- Domínio puro: agregados (`AdminSupplier`, `AdminContract`, `AdminExpense`), VOs, exceções, eventos,
  repositórios (Spring Data), fachada `AdminService`. Sem dependência de `application`/`infra`
  (ArchUnit). `internal/*` module-private.
- Delivery: `AdminController` em `application.api` + DTOs em `application.api.dto` (entity-free).
- Infra: `AdminContractExpiryScheduler` em `infra.jobs` (só fornece relógio/horizonte ao domínio,
  via `GovernedJobs`).
- Sem FK cross-contexto: `documentId`, `financeEntryId` são **valores** (uuid), não FKs;
  `supplierId` é FK **interna** do Admin (contrato/despesa → fornecedor, mesmo módulo) — permitido.
- `DomainException` com `code == chave i18n`; pt-BR + fallback en-US. Sem exceção crua de banco
  vazando (índice único → erro de negócio traduzido).
- Migração `V30__create_admin.sql` idempotente; nunca editar migração aplicada (V8 do Compliance
  intacta — o seed do `SERVICE` é **acrescentado** na V30).
- OpenAPI atualizada; observabilidade (evento de negócio logado, correlation id; identificadores de
  fornecedor — CNPJ/CPF — **mascarados** em log/auditoria, pois podem ser PII de autônomo).
- Segurança: escritas exigem `ROLE_FINANCE` (DL-0088); `TestSecurityConfig` mantém os 444 testes
  verdes; teste novo de segurança sobe o caminho JWT real (403/201).

## Modelo de domínio

```
AdminSupplier (aggregate, tabela admin_suppliers)
  id UUID PK
  type AdminSupplierType { UTILITY, SOFTWARE, SERVICE, OTHER }   (BR1)
  identifier String (null; CNPJ/CPF quando aplicável)            (BR1 — mascarado em log)
  displayName String (obrigatório)
  status AdminSupplierStatus { ACTIVE, INACTIVE }  (nasce ACTIVE)
  created/updated/by, version

AdminContract (aggregate, tabela admin_contracts)
  id UUID PK
  supplierId UUID (FK interna → admin_suppliers)                 (BR2)
  validFrom LocalDate (obrigatório); validUntil LocalDate (null = aberto)
  recurrence AdminRecurrence { MONTHLY, YEARLY, OTHER } (null)
  amount Money (numeric(18,2)+currency, null)                    (BR2)
  documentId UUID (null; Compliance, valor — BR2)
  expirySignaledAt Instant (null; idempotência do alerta — DL-0087)
  created/updated, version
  Métodos: register(...) valida janela (validUntil >= validFrom);
           signalExpiringIfDue(now, asOf, horizon) idempotente

AdminExpense (aggregate, tabela admin_expenses)
  id UUID PK
  supplierId UUID; period char(7) (YYYY-MM); amount Money; kind AdminExpenseKind
  financeEntryId UUID (null; Finance, valor — preenchido na criação do lançamento)
  created_at, created_by
  UNIQUE (supplier_id, period, kind)  — idempotência (DL-0086)

AdminExpenseKind { UTILITY, AUTONOMOUS_SERVICE, SERVICE, OTHER }
  → EntryType: UTILITY→UTILITY_EXPENSE; AUTONOMOUS_SERVICE→AUTONOMOUS_SERVICE;
               SERVICE→SERVICE; OTHER→OTHER_EXPENSE   (DL-0085, função pura)
```

Exceções: `AdminSupplierNotFoundException` (404 `admin.supplier.not-found`),
`AdminSupplierInvalidException` (400), `AdminContractInvalidException` (400
`admin.contract.invalid`), `AdminExpenseDuplicateException` (409 `admin.expense.duplicate`),
`AdminExpenseInvalidException` (400). Eventos: `AdminSupplierRegistered {supplierRef, occurredAt}`,
`AdminContractRegistered {supplierRef, occurredAt}`, `AdminExpenseRegistered {expenseId,
financeEntryId, entryType, occurredAt}`, `AdminContractExpiring {contractId, validUntil, occurredAt}`.

## API (REST)

| Método | Rota | Resultado |
|---|---|---|
| POST | `/api/admin/suppliers` `{type,identifier,displayName}` | 201 `AdminSupplierView` (ACTIVE) · **FINANCE** |
| GET | `/api/admin/suppliers/{id}` | 200 / 404 `admin.supplier.not-found` |
| GET | `/api/admin/suppliers?type=&status=` | 200 lista (filtros combináveis) |
| POST | `/api/admin/suppliers/{id}/contracts` `{validFrom,validUntil,recurrence,amount,documentId}` | 201 `AdminContractView` · **FINANCE** |
| GET | `/api/admin/suppliers/{id}/contracts` | 200 lista |
| POST | `/api/admin/expenses` `{supplierId,period,amount,kind}` | 201 `{id,financeEntryId,requiredDocuments[]}` · **FINANCE** |
| POST | `/api/admin/contracts/flag-expiring` | 200 `{flagged}` (gatilho sweep) · **FINANCE** |

## Persistência — `V30__create_admin.sql`

Tabelas `admin_suppliers`, `admin_contracts` (+ `expiry_signaled_at`), `admin_expenses` conforme
spec. Índices: `admin_suppliers (type, status)`; `admin_contracts (supplier_id)` + índice parcial
`expires`(`valid_until WHERE valid_until IS NOT NULL AND expiry_signaled_at IS NULL`);
UNIQUE `ux_admin_expenses_supplier_period_kind (supplier_id, period, kind)`. FK interna
`admin_contracts.supplier_id`/`admin_expenses.supplier_id → admin_suppliers(id)`. **Seed aditivo** do
Compliance: `INSERT INTO document_requirements` do `SERVICE` (`NFSE` AT_REGISTRATION; `PAYMENT_PROOF`
AT_SETTLEMENT) — a V8 não é editada.

## Fatias (ordem de dependência)

### Fatia 8l-1 — Fornecedores + contratos (BR1/BR2/BR6) + gate FINANCE
- **RED:** unit de domínio (janela de contrato inválida; status); integração HTTP: POST fornecedor
  → 201 ACTIVE + `AdminSupplierRegistered`; GET inexistente → 404; lista por `type`/`status`; POST
  contrato → 201 + `AdminContractRegistered`; contrato com `validUntil < validFrom` → 400. Segurança:
  POST sem `ROLE_FINANCE` (token real) → 403 + auditoria; com FINANCE → 201.
- **GREEN:** módulo `admin` (agregados supplier/contract, VOs, exceções, repos, `AdminService`,
  eventos), migração V30 (suppliers+contracts), controller + DTOs, i18n, OpenAPI, `package-info`
  `@ApplicationModule`, matchers de segurança, auditoria `ADMIN_CHANGE`.
- **GATES + DoD:** ArchUnit/Modulith/Spotless verdes; `./mvnw verify` verde; caderno; Conventional
  Commits; merge `--no-ff` na integração; re-verify.

### Fatia 8l-2 — Despesa recorrente → Finance + documentos exigidos (BR3/BR4/DL-0085/DL-0086)
- **RED (inclui a regressão da regra de ouro):** integração — registrar despesa de energia (UTILITY)
  cria `LedgerEntry` PAYABLE/UTILITY_EXPENSE no Finance e responde `requiredDocuments`
  `[UTILITY_BILL]`; despesa duplicada `(supplier, period, kind)` → 409 `admin.expense.duplicate`
  (sem 2º lançamento); SERVICE → `SERVICE`/`[NFSE]`; autônomo → `AUTONOMOUS_SERVICE`/`[RPA]`.
  **Regressão (a regra de ouro):** a despesa administrativa **sem** documento bloqueia o fechamento
  do Finance via Compliance (`close-check canClose=false` listando o lançamento da despesa) — falha
  antes (lançamento existe, doc não), passa depois de anexar.
- **GREEN:** porta `DocumentRequirementDirectory` no Compliance (+ impl no `ComplianceService`);
  `EntryType` +SERVICE/+OTHER_EXPENSE; seed `SERVICE` na V30; `AdminService.registerExpense(...)`
  (chama `FinanceService.register`, guarda `financeEntryId`, consulta documentos exigidos, UNIQUE +
  pré-check); controller `POST /api/admin/expenses`; i18n; OpenAPI.
- **GATES + DoD:** idem; caderno; merge `--no-ff`; re-verify.

### Fatia 8l-3 — Alerta de contrato a vencer (BR5/DL-0087)
- **RED:** integração — contrato com `validUntil` em 10 dias; `flagExpiringContracts(now)` publica
  `AdminContractExpiring` **uma vez** (idempotente no 2º sweep); contrato distante (90 dias) não
  dispara; o `AdminContractExpiryScheduler` está fiado (e registra `JobRun`).
- **GREEN:** `AdminService.flagExpiringContracts(Instant)` + `signalExpiringIfDue`;
  `AdminContractExpiryScheduler` em `infra.jobs` (via `GovernedJobs`); registra o job no catálogo do
  Platform (seed/registro como os demais); endpoint `POST /api/admin/contracts/flag-expiring`; índice
  parcial já em V30.
- **GATES + DoD:** idem; caderno; merge `--no-ff`; re-verify.

## Testes (proporcionais)

- **Unit/domain:** `AdminContractTest` (janela; `signalExpiringIfDue` idempotente),
  `AdminExpenseKindTest` (mapa `kind`→`EntryType`, BR3/DL-0085), estados de fornecedor.
- **Integração (Testcontainers/Postgres real):** `AdminSupplierContractIntegrationTest` (jornada REST
  + sad paths + segurança 403/201), `AdminExpenseIntegrationTest` (lançamento no Finance + documentos
  exigidos + duplicidade + **regressão do veto de fechamento**), `AdminContractExpiryIntegrationTest`
  (sweep + `AdminContractExpiring` via `@RecordApplicationEvents` + scheduler fiado).
- **Arquitetura:** ArchUnit (domínio não depende de application/infra; sem setters em @Entity; sem
  *Impl) + Spring Modulith `verify()` (22º módulo, acíclico; `admin → {finance, compliance, platform,
  money, error}`).
- **Segurança:** o caminho JWT real (token mintado pelo emissor real) prova 403 sem `ROLE_FINANCE` e
  201 com — sem afrouxar o gate (DL-0081/DL-0088).
- **Smoke:** `/api/system/health` já coberto pela fundação (não regride).

## Definition of Done (por fatia)

Critérios de aceite viram teste e passam; `./mvnw verify` verde (ArchUnit + Modulith + Checkstyle +
Spotless); V30 idempotente; `DomainException` code==i18n; sem exceção de banco vazando; OpenAPI
atualizada; observabilidade (evento logado, identificador mascarado, correlation id); Conventional
Commits; caderno de testes atualizado antes do merge; manual bilíngue atualizado ao fim da fase.

## Documentação (fim da fase)

- `docs/test-report/8l-1.md`, `8l-2.md`, `8l-3.md` + INDEX.
- `docs/release-notes/0.20.0.md` (pt-BR) + append em `docs/release-notes/CHANGELOG.en-US.md`.
- `docs/MANUAL.md` (pt-BR) + `docs/MANUAL.en-US.md` em sincronia (nova capacidade: cadastro de
  fornecedor/contrato administrativo, registro de despesa que gera lançamento + documentos exigidos,
  alerta de contrato a vencer).
- Relatório final em `docs/relatorio-fase-8l.md`.

## Riscos / fora de escopo

- **Procurement completo** (cotação/aprovação/ordem de compra) = comprar (DL-0084) — seam preservado.
- O **razão e o fechamento** são do Finance; o **cofre/requisito** é do Compliance — Admin só **gera**
  o lançamento e **referencia** o documento (BR4). O veto continua sendo Finance+Compliance.
- Fornecedores de **turismo/marcas** são do Portfolio (SPEC-0020) — não confundir.
- Frontend Angular do Admin não está no escopo desta spec (entrega é registro/API; UX profissional é
  a Fase 10).
- **Fim do bloco 8x.** As próximas fases (9–15) são estruturais/UX/observabilidade/E2E/IdP/upgrade.
