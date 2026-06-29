# 0025 - Admin (Fornecedores e Contratos Administrativos)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. **Subdomínio genérico** (redesenho linha 138/165): entrega
> **enxuta e real** — cadastro de **fornecedores administrativos** (luz, água, telefone, software) e
> seus **contratos**, alimentando lançamentos no **Finance (SPEC-0015)** e documentos no **Compliance
> (SPEC-0008)**. Não confundir com os **fornecedores de turismo/marcas** (Portfolio, SPEC-0020).

## Goal

Dar à empresa um registro simples dos **custos administrativos recorrentes** e dos **contratos** que os
sustentam, de modo que cada despesa (conta de consumo, mensalidade) gere o **lançamento** certo e
referencie o **documento hábil** exigido pelo fechamento (fatura + comprovante; contrato) — redesenho
linha 138, tabela 7.7.

## Scope

**Em escopo:** o agregado `AdminSupplier` (fornecedor administrativo: tipo UTILITY | SOFTWARE | SERVICE |
OTHER; identificação; status); `AdminContract` (fornecedor, vigência, valor recorrente, documento de
contrato no Compliance); registro de **despesa recorrente** que **cria o lançamento** no Finance com o
`entryType` correto (UTILITY_EXPENSE, etc.) e amarra o documento exigido (UTILITY_BILL + PAYMENT_PROOF;
RPA para autônomo); alerta de **contrato a vencer**.

**Fora de escopo:** **compras/cotação** de fornecedores (procurement completo — comprar, se exigido); o
**razão e o fechamento** (Finance); o **cofre/requisito** (Compliance); fornecedores de **turismo**
(Portfolio).

## Business Context

Contas de consumo **não geram NF** — o documento hábil é **fatura + comprovante** (7.7); serviço de
autônomo exige **RPA**; software/serviço PJ exige **NF**. Modelar o fornecedor/contrato administrativo
garante que a despesa recorrente caia no lançamento certo, com o documento certo, para o mês **fechar**.
É o "balcão administrativo" que alimenta Finance/Compliance sem inventar regra fiscal nova.

## Business Rules

```txt
BR1  AdminSupplier MUST ter type, identifier (CNPJ/CPF quando aplicável) e status ACTIVE/INACTIVE.
BR2  AdminContract MUST ter supplier, validFrom/validUntil, recurrence (ex.: mensal), amount (Money) e
     referência ao documento de contrato no Compliance (quando houver contrato).
BR3  Registrar uma despesa recorrente MUST criar um LedgerEntry PAYABLE no Finance com o entryType
     correto pelo tipo (UTILITY → UTILITY_EXPENSE; autônomo → AUTONOMOUS_SERVICE; PJ → SERVICE) e
     sinalizar o(s) documento(s) exigido(s) pelo DocumentRequirement (Compliance).
BR4  Admin MUST NOT impor a regra de documento nem fechar período — apenas **gerar o lançamento** e
     **referenciar** o documento; o veto/fechamento é Finance+Compliance.
BR5  Contrato a vencer MUST publicar AdminContractExpiring (alerta — não bloqueia).
BR6  Toda alteração de fornecedor/contrato MUST ser auditada.
```

## Input/Output Examples

```http
POST /api/admin/suppliers
{ "type":"UTILITY", "identifier":"61695227000193", "displayName":"Companhia de Energia" }
201 Created  { "id":"asp1...", "status":"ACTIVE" }

POST /api/admin/expenses
{ "supplierId":"asp1...", "period":"2026-06", "amount":{"amount":"840.00","currency":"BRL"},
  "kind":"UTILITY" }
201 Created
{ "id":"exp1...", "financeEntryId":"e07...", "requiredDocuments":["UTILITY_BILL","PAYMENT_PROOF"] }
```

## API Contracts

- `POST /api/admin/suppliers` / `GET .../suppliers/{id}` / `GET .../suppliers?type=&status=` → CRUD + lista.
- `POST /api/admin/suppliers/{id}/contracts` — registra contrato (vincula documento) → 201.
- `POST /api/admin/expenses` — registra despesa recorrente → cria lançamento no Finance + lista documentos exigidos → 201.
- OpenAPI atualizada.

## Events

- `AdminSupplierRegistered` / `AdminContractRegistered` — `{supplierRef, occurredAt}`. Produtor: `admin`.
- `AdminExpenseRegistered` — `{expenseId, financeEntryId, entryType, occurredAt}`. Consumidor: `finance`,
  `compliance` (rastreia documento exigido), `intelligence` (custo fixo).
- `AdminContractExpiring` — `{contractId, validUntil, occurredAt}` (alerta).

## Persistence Changes

```txt
V25__create_admin.sql
  admin_suppliers( id uuid PK, type varchar not null, identifier varchar null, display_name varchar not null,
                   status varchar not null, created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null )
  admin_contracts( id uuid PK, supplier_id uuid not null REFERENCES admin_suppliers(id),
                   valid_from date not null, valid_until date null, recurrence varchar null,
                   amount numeric(18,2) null, currency varchar null, document_id uuid null,   -- valor p/ Compliance
                   created_at, updated_at timestamptz not null, version bigint not null )
  admin_expenses( id uuid PK, supplier_id uuid not null, period char(7) not null,
                  amount numeric(18,2) not null, currency varchar not null, kind varchar not null,
                  finance_entry_id uuid null,                 -- valor p/ Finance (lançamento gerado)
                  created_at timestamptz not null, created_by varchar null )
```

O lançamento é criado via **fachada** do Finance (sem FK); o documento exigido é o do Compliance
(`DocumentRequirement`). Alerta de vencimento por **job**.

## Validation Rules

- Application: existência do fornecedor; criação idempotente do lançamento (não duplica por despesa);
  mapeamento tipo→entryType correto (BR3).
- Domain: estados de fornecedor/contrato; alerta de vencimento (BR5).
- Princípio: Admin não fecha período nem impõe documento (BR4).

## Error Behavior

`admin.supplier.not-found` → 404; `admin.expense.duplicate` → 409; `admin.contract.invalid` → 400.
i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar cadastro/despesa/vencimento como evento de negócio (supplierRef, correlation id). Métricas:
  `admin_expenses_total{kind}`, `admin_contracts_expiring_total`.

## Tests Required

- **Unit/domain:** mapeamento tipo→entryType (UTILITY→UTILITY_EXPENSE, autônomo→AUTONOMOUS_SERVICE);
  estados de fornecedor/contrato.
- **Integração (Testcontainers):** registrar despesa de energia cria lançamento PAYABLE no Finance e
  lista UTILITY_BILL+PAYMENT_PROOF como exigidos; contrato a vencer publica `AdminContractExpiring`.
- **Regressão:** a despesa administrativa **sem** documento bloqueia o fechamento do Finance via
  Compliance (falha antes, passa depois) — a regra de ouro vale também para o administrativo.

## Acceptance Criteria

- Registrar fornecedor de energia + despesa do mês cria o lançamento certo e aponta os documentos exigidos.
- Contrato a vencer gera alerta.
- A conta de consumo sem fatura/comprovante impede o mês de fechar (via Finance/Compliance).
- `./mvnw verify` verde.

## Open Questions

- **Procurement completo** (cotação/aprovação de compra) — se exigido, **comprar**; este módulo fica como
  cadastro/seam — decisão do dono.
- Mapa final **tipo de despesa → entryType → DocumentRequirement** — compartilhado com Finance/Compliance.

## Out of Scope

Procurement/cotação completos (comprar), razão e fechamento (SPEC-0015), cofre/requisito (SPEC-0008),
fornecedores de turismo/marcas (SPEC-0020).
