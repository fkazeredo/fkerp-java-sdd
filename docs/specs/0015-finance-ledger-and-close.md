# 0015 - Finance (Razão AP/AR + Fechamento Mensal)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. **Subdomínio genérico** (redesenho linha 135: "avaliar
> comprar"): a entrega aqui é o **mínimo necessário** de AP/AR + período para o resto do sistema —
> não um razão contábil completo. É **pré-requisito do Compliance (SPEC-0008)** e, na prática,
> **co-entrega na Fatia 2**.

## Goal

Manter os **razões de Contas a Pagar e a Receber** e a **máquina de período contábil** com fechamento
mensal — o ponto onde o **veto do Compliance** (lançamento sem documento obrigatório) efetivamente
**impede o mês de fechar** (redesenho linha 140 e 239). É o livro-caixa operacional do ERP, não o ERP
contábil definitivo.

## Scope

**Em escopo:** o agregado `LedgerEntry` (direção PAYABLE/RECEIVABLE, parte, valor, **tipo de lançamento**
— a chave que o Compliance usa para exigir documento, status PROVISIONAL/CONFIRMED/SETTLED, referência
ao período); o agregado `AccountingPeriod` (YYYY-MM, status OPEN/CLOSING/CLOSED) e a operação
**`closePeriod`** que consulta o Compliance (`close-check`) e **falha se houver pendência**; consumo de
eventos que geram lançamentos (comissão, multa, repasse).

**Fora de escopo:** plano de contas/partidas dobradas, DRE, fluxo de caixa projetado, SPED/ECD — se o
cliente exigir contabilidade plena, **integrar/comprar** um ERP contábil (esta spec define o seam). O
**cofre/retenção/requisito** é do Compliance; o **certificado** é do Platform.

## Business Context

A contabilidade lança "devo R$ X ao fornecedor Y" e "tenho a receber R$ Z da agência W"; no fim do mês,
**não fecha sem a nota** (linha 239, "em miúdos"). Finance é dono do **registro do que se deve/recebe e
do calendário de fechamento**; Compliance é dono da **regra do documento**. Separar os dois mantém a
regra de retenção/obrigatoriedade num lugar só.

## Business Rules

```txt
BR1  LedgerEntry MUST ter direction ∈ {PAYABLE, RECEIVABLE}, party (id+tipo, valor), amount (Money),
     entryType (ex.: COMMISSION_RECEIVABLE, COMMISSION_PAYABLE, PENALTY, UTILITY_EXPENSE,
     AUTONOMOUS_SERVICE, SUPPLIER_SETTLEMENT, REFUND), period (YYYY-MM) e status.
BR2  status: PROVISIONAL (criado, pode faltar documento) → CONFIRMED (validado) → SETTLED (pago/recebido,
     via Payout). Lançamento pode nascer PROVISIONAL (linha 239).
BR3  AccountingPeriod.status: OPEN → CLOSING → CLOSED. closePeriod(period) MUST:
       (a) chamar Compliance.close-check(period);
       (b) se canClose=false, **falhar** com finance.period.cannot-close listando as pendências (não
           fecha); (c) se canClose=true, marcar CLOSED e publicar PeriodClosed.
BR4  Lançamento em período CLOSED MUST ser rejeitado (finance.period.closed); ajustes vão para período aberto.
BR5  Eventos de negócio viram lançamentos: ExpectedCommissionAccrued → RECEIVABLE/PAYABLE de comissão;
     CancellationCharged → PENALTY/REFUND; SupplierSettlement → SUPPLIER_SETTLEMENT (consumo idempotente).
BR6  Finance MUST NOT impor a regra de documento — apenas **consultar** o Compliance e respeitar o veto.
BR7  ASSUMIDO (ver DL-0014): entrega-se agora o **seam mínimo** (AP/AR + período). Contabilidade plena
     (partidas dobradas, DRE, SPED/ECD) NÃO é construída; se exigida, integra-se/compra-se um ERP
     contábil e este módulo vira adaptador (as portas `CloseGuard`/`LedgerDirectory` isolam isso).
BR8  ASSUMIDO (ver DL-0013): o razão guarda cada lançamento em **moeda original** (Money), sem
     conversão; o total do período agrega **por moeda** (nunca soma moedas diferentes).
BR9  ASSUMIDO (ver DL-0012): o mapa `entryType × DocumentRequirement` é compartilhado com o Compliance
     (seed da tabela 7.7); o veto de fechamento usa os requisitos de fase AT_REGISTRATION.
```

## Input/Output Examples

```http
POST /api/finance/entries
{ "direction":"PAYABLE", "party":{"id":"sup-12","type":"SUPPLIER"},
  "amount":{"amount":"2850.00","currency":"BRL"}, "entryType":"SUPPLIER_SETTLEMENT", "period":"2026-06" }
201 Created  { "id":"e91...", "status":"PROVISIONAL" }

POST /api/finance/periods/2026-06/close
409 Conflict
{ "code":"finance.period.cannot-close",
  "pending":[ {"entryId":"e07...","type":"UTILITY_EXPENSE","missing":["UTILITY_BILL"]} ] }
```

## API Contracts

- `POST /api/finance/entries` — cria lançamento → 201. `POST .../entries/{id}/confirm` → 200.
- `GET /api/finance/entries?direction=&status=&period=&party=&page=&size=` → `PageResponse`.
- `POST /api/finance/periods/{yyyymm}/close` → 200 (fechado) | 409 `finance.period.cannot-close`.
- `GET /api/finance/periods/{yyyymm}` → status + totais AP/AR.
- OpenAPI atualizada.

## Events

- `LedgerEntryRegistered` — `{entryId, direction, entryType, period, occurredAt}`. Produtor: `finance`.
  Consumidor: `compliance` (rastreia conformidade do lançamento), `intelligence`.
- `PeriodClosed` — `{period, occurredAt}`. Consumidor: `billing`, `intelligence`, `platform` (auditoria).

## Persistence Changes

```txt
V15__create_finance.sql
  ledger_entries(
    id uuid PK, direction varchar not null, party_id varchar not null, party_type varchar not null,
    amount numeric(18,2) not null, currency varchar not null, entry_type varchar not null,
    period char(7) not null, status varchar not null, document_ref uuid null,   -- valor p/ Compliance
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null,
    INDEX ix_ledger_period_status (period, status)
  )
  accounting_periods( period char(7) PK, status varchar not null, closed_at timestamptz null, closed_by varchar null )
```

`closePeriod` é transição financeira → **locking pessimista** no período. O `close-check` ao Compliance
é consulta cross-módulo por **fachada/porta** (sem FK). Se o cliente comprar um ERP contábil, este
módulo vira **adaptador** que sincroniza lançamentos/fechamento.

## Validation Rules

- Application: idempotência no consumo de eventos (não duplica lançamento); período existente/aberto.
- Domain: máquina de período (BR3/BR4) e estados do lançamento (BR2) como invariantes.
- Integração: `closePeriod` respeita o veto do Compliance (BR3/BR6).

## Error Behavior

`finance.period.cannot-close` → 409 (com pendências); `finance.period.closed` → 409 (lançar em fechado);
`finance.entry.not-found` → 404. i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar criação/confirmação de lançamento e tentativa/sucesso de fechamento (period, pendências,
  correlation id). Métricas: `ledger_entries_total{direction,type}`, `period_close_blocked_total`,
  saldos AP/AR por período.

## Tests Required

- **Unit/domain:** máquina de período (OPEN→CLOSING→CLOSED) e bloqueio de lançamento em CLOSED.
- **Integração (Testcontainers):** fechar período com pendência de documento → 409 com lista (consulta
  Compliance fake/real); fechar período conforme → CLOSED + `PeriodClosed`; evento de comissão vira
  lançamento (idempotente).
- **Regressão (a regra de ouro do compliance):** lançamento sem documento exigido **bloqueia** o
  fechamento (falha antes, passa depois).

## Acceptance Criteria

- Lançar "devo ao fornecedor" cria entry PROVISIONAL; sem a nota, o mês **não fecha** (409 com o que falta).
- Com a nota anexada (Compliance), o mesmo período fecha e publica `PeriodClosed`.
- `./mvnw verify` verde.

## Open Questions

- ~~**Comprar vs. construir**~~ → **ASSUMIDO (ver DL-0014)**: construir o seam mínimo agora; comprar a
  contabilidade plena depois (ver BR7). Continua sendo **decisão do dono** quando a contabilidade
  plena for exigida.
- ~~Mapa final `entryType × DocumentRequirement`~~ → **ASSUMIDO (ver DL-0012)**: seed compartilhado
  com o Compliance (ver BR9).
- ~~Tratamento de **multimoeda** no razão~~ → **ASSUMIDO (ver DL-0013)**: moeda original, sem
  conversão (ver BR8).

## Out of Scope

Plano de contas/partidas dobradas, DRE, SPED/ECD (comprar/integrar); cofre/retenção (SPEC-0008);
certificado (SPEC-0023).
