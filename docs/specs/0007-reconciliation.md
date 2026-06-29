# 0007 - Reconciliation (Conciliação)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001 §"Convenções do projeto"** (dinheiro, datas, erros, auditoria).

## Goal

Fechar o ciclo econômico do núcleo: para cada venda confirmada, **cruzar o que se espera receber, o
que se espera pagar, a comissão esperada × realizada e o ganho/perda cambial**, e responder com
número: *aquela venda virou margem ou foi vaidade?* (redesenho 7.5).

## Scope

**Em escopo:** abrir um `ReconciliationCase` quando uma `Booking` é confirmada; registrar os valores
**realizados** (recebido da agência em BRL, pago ao fornecedor em BRL pela **taxa de liquidação**,
comissão recebida do fornecedor, comissão paga ao agente); calcular **spread realizado**,
**ganho/perda cambial por caso** e **variância de comissão**; sinalizar discrepância acima de
tolerância; tela de lista priorizada por valor de discrepância.

**Fora de escopo:** a **posição agregada do livro** e a decomposição **subsídio × drift** do câmbio
(visão de carteira/forward) são a **SPEC-0011**; a automação dos pagamentos (Payout, SPEC-0017) e o
razão de AP/AR (Finance, SPEC-0015) — aqui os realizados são **registrados** contra o caso.

## Business Context

As pontas vivem em **tempos e moedas diferentes**: a agência paga em reais agora; o fornecedor é pago
em dólar depois. O Quoting congelou na composição a taxa de venda e as comissões (proveniência), e o
Commissioning derivou o spread esperado. A conciliação compara esse **esperado** com o **realizado**
e isola o efeito câmbio. É a fatia que dá a verdade financeira por venda.

## Business Rules

```txt
BR1  Ao receber BookingConfirmed, o sistema MUST abrir exatamente um ReconciliationCase em estado
     OPEN, copiando do Quote (via proveniência já congelada): baseAmount+currency, pinnedRate,
     baseBrl, expectedSupplierCommission, expectedAgentCommission, expectedSpread. (Idempotente por
     bookingId — reentrega não duplica caso.)
BR2  Ao receber BookingCancelled, o caso correspondente MUST ir para CANCELLED (sem realizados).
BR3  Os realizados são registrados explicitamente: amountReceivedFromAgency (BRL),
     supplierSettlementRate (escala 6, > 0) + supplierPaidAmount (BRL), commissionReceivedFromSupplier,
     commissionPaidToAgent. Cada registro é auditado.
BR4  realizedSpread = (amountReceivedFromAgency − supplierPaidAmount) − commissionPaidToAgent
                      + commissionReceivedFromSupplier. (Derivado; nunca digitado.)
BR5  fxGainLoss = (pinnedRate − supplierSettlementRate) × supplierAmount(na moeda estrangeira).
     Positivo = ganho (mercado moveu a favor); negativo = perda. Derivado.
BR6  Um caso vira SETTLED quando todas as pontas têm realizado; PARTIALLY_SETTLED se só algumas.
BR7  Se |realizedSpread − expectedSpread| exceder a tolerância configurada (parâmetro governado,
     default a confirmar), o caso MUST ser marcado DISCREPANCY e publicar ReconciliationDiscrepancyFlagged.
BR8  Reconciliation é leitura/derivação sobre fatos; MUST NOT alterar Booking/Quote/Commissioning.
```

## Input/Output Examples

```http
POST /api/reconciliation/{caseId}/settlement
{ "amountReceivedFromAgency": {"amount":"3000.00","currency":"BRL"},
  "supplierSettlementRate": "5.700000",
  "supplierPaidAmount": {"amount":"2850.00","currency":"BRL"},
  "commissionReceivedFromSupplier": {"amount":"405.00","currency":"BRL"},
  "commissionPaidToAgent": {"amount":"270.00","currency":"BRL"} }
200 OK
{ "caseId":"c12...", "status":"SETTLED",
  "expectedSpread":{"amount":"135.00","currency":"BRL"},
  "realizedSpread":{"amount":"285.00","currency":"BRL"},
  "fxGainLoss":{"amount":"-150.00","currency":"BRL"} }   # pinned 5.40 vs settle 5.70 em USD 500
```

## API Contracts

- `GET /api/reconciliation/{caseId}` → 200 | 404 `reconciliation.case.not-found`.
- `GET /api/reconciliation?status=&minDiscrepancy=&page=&size=` → `PageResponse`, **sort default por
  valor de discrepância desc** (priorização — DSS consome depois).
- `POST /api/reconciliation/{caseId}/settlement` — registra (parcial ou total) os realizados → 200.
- Casos não são criados por API: nascem de `BookingConfirmed`. OpenAPI atualizada.

## Events

- `ReconciliationCaseOpened` — `{caseId, bookingId, occurredAt}`. Produtor: `reconciliation`.
- `SpreadRealized` — `{caseId, realizedSpread, fxGainLoss, occurredAt}` (7.1). Consumidor:
  `intelligence` (margem real, counterfactual).
- `ReconciliationDiscrepancyFlagged` — `{caseId, expectedSpread, realizedSpread, delta, occurredAt}`.
  Consumidor: `intelligence` (divergências priorizadas, 8.2-H).

## Persistence Changes

```txt
V6__create_reconciliation_cases.sql
  reconciliation_cases(
    id uuid PK,
    booking_id uuid not null UNIQUE,        -- idempotência por reserva (BR1)
    base_amount numeric(18,2) not null, base_currency varchar not null,
    pinned_rate numeric(18,6) not null,
    base_brl numeric(18,2) not null,
    expected_supplier_commission_brl numeric(18,2) not null,
    expected_agent_commission_brl numeric(18,2) not null,
    expected_spread_brl numeric(18,2) not null,
    -- realizados (nullable até registrar)
    amount_received_from_agency_brl numeric(18,2) null,
    supplier_settlement_rate numeric(18,6) null,
    supplier_paid_brl numeric(18,2) null,
    commission_received_from_supplier_brl numeric(18,2) null,
    commission_paid_to_agent_brl numeric(18,2) null,
    realized_spread_brl numeric(18,2) null,
    fx_gain_loss_brl numeric(18,2) null,
    status varchar not null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null,
    version bigint not null
  )
```

Transição de liquidação é operação financeira → **locking pessimista** (`getRequiredForUpdate`,
`persistence.md`). A priorização por discrepância é **read-model/projeção** (não force pelo agregado).

## Validation Rules

- Delivery: Bean Validation (moedas, taxa > 0).
- Application: existência do caso; idempotência por `bookingId` ao abrir.
- Domain: derivações de BR4/BR5 protegidas no agregado; tolerância (BR7) como política.
- Integração: consumo idempotente de `BookingConfirmed`/`BookingCancelled` (state check + UNIQUE).

## Error Behavior

`reconciliation.case.not-found` → 404; `reconciliation.currency.mismatch` → 400 (realizado em moeda
diferente de BRL onde se espera BRL). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar abertura/liquidação/discrepância como eventos de negócio (caseId, deltas, correlation id).
- Métricas: `reconciliation_cases_open`, `reconciliation_discrepancies_total`, soma de `fxGainLoss`.

## Tests Required

- **Unit/domain:** `realizedSpread` e `fxGainLoss` (incluindo o exemplo USD 500, pinned 5.40 vs 5.70
  → fxGainLoss −150); marcação de DISCREPANCY na tolerância.
- **Integração (Testcontainers):** `BookingConfirmed` abre caso (idempotente); registro de liquidação
  total → SETTLED; parcial → PARTIALLY_SETTLED; `BookingCancelled` → CANCELLED.
- **Regressão:** reentrega de `BookingConfirmed` não cria caso duplicado (UNIQUE booking_id).

## Acceptance Criteria

- Confirmar a venda de Orlando abre um caso com `expectedSpread` 135 BRL.
- Registrar liquidação a 5,70 resulta em `realizedSpread` e `fxGainLoss` corretos e estado SETTLED.
- A listagem ordena por discrepância desc.
- `./mvnw verify` verde.

## Open Questions

- **Tolerância de discrepância** (valor/percentual default) — parâmetro governado; confirmar com o
  dono (vira `CommercialPolicy` na SPEC-0014).
- Conciliação **multi-booking** (uma fatura do fornecedor cobre N reservas) — adiada; aqui 1 caso : 1
  booking.

## Out of Scope

Posição agregada do livro e subsídio × drift (SPEC-0011), razão AP/AR (SPEC-0015), automação de
pagamentos (SPEC-0017).
