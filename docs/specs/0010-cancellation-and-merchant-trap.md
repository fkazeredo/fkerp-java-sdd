# 0010 - Cancelamento como Objeto + Armadilha do Merchant + No-Show

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Graduamos o cancelamento **simples** da SPEC-0006 em
> **política como objeto** (redesenho 7.3/7.4). Não inventa motor de cobrança — emite os fatos para
> Finance/Payout/AfterSales consumirem.

## Goal

Modelar a **política de cancelamento como dado** (janelas de multa, reembolsável, quem paga) e a
**armadilha do `ALL_SALES_FINAL`**: em venda merchant, o portal é cobrado pela marketplace **mesmo
reembolsando o cliente** — **duas obrigações distintas que não se anulam** (redesenho 7.4). Inclui a
`NoShowPolicy` de carro.

## Scope

**Em escopo:** `CancellationPolicy` (type STANDARD | ALL_SALES_FINAL | CUSTOM; `windows[{hoursBefore,
penaltyPct}]`; `refundable`; `costBearer`) **congelada na Booking** na confirmação; cálculo da **multa**
pela janela aplicável; modelagem das **duas obrigações** no caso `ALL_SALES_FINAL` (custo irrecuperável
do portal × reembolso ao cliente); `NoShowPolicy` (fee, `waivedIfFlightCancelled`); eventos que
materializam os encargos.

**Fora de escopo:** execução do reembolso (Payout, SPEC-0017) e o chamado de pós-venda (AfterSales,
SPEC-0018); o lançamento contábil (Finance, SPEC-0015). A **reversão das comissões** já ocorre via
`BookingCancelled → CommissionReversed` (SPEC-0006/0004) — aqui só somamos a multa/obrigações.

## Business Context

Cancelar estorna as duas pontas de comissão (3.2), mas a **multa** e o **custo merchant** são fatos
adicionais. A sutileza do `ALL_SALES_FINAL`: a Acme/portal continua **devendo à marketplace** o custo
da reserva ainda que, por decisão comercial, **reembolse o cliente** — são obrigações separadas. Tratar
como uma só (anular) **perde dinheiro de forma invisível**.

## Business Rules

```txt
BR1  Na confirmação, a Booking MUST congelar a CancellationPolicy vigente (snapshot, como a
     proveniência do Quote). O cancelamento usa a política congelada, não a atual.
BR2  type STANDARD: multa = penaltyPct da janela cujo hoursBefore é o menor limite ≥ (horas até o
     serviço). Sem janela aplicável => multa 0.
BR3  type ALL_SALES_FINAL: refundable = false do ponto de vista do fornecedor; o custo ao fornecedor
     é devido integralmente. Se houver reembolso ao cliente (decisão comercial), ele é uma obrigação
     SEPARADA — as duas coexistem (BR5).
BR4  type CUSTOM: usa as janelas informadas; sem janelas => comporta-se como STANDARD com multa 0.
BR5  Ao cancelar, o sistema MUST registrar os encargos resultantes como fatos distintos:
       - SupplierCharge (o que se deve ao fornecedor/marketplace), quando aplicável;
       - CustomerRefund (o que se devolve ao cliente), quando aplicável;
       - PenaltyCharge (a multa), com costBearer ∈ {AGENCY, ACME, SUPPLIER}.
     Eles NÃO se compensam automaticamente.
BR6  NoShowPolicy (carro): em NO_SHOW, cobra fee; se waivedIfFlightCancelled = true e houver prova de
     voo cancelado, a fee é dispensada (a prova é documento — Compliance).
BR7  Todo cancelamento/no-show com encargo MUST ser auditado (quem, quando, política aplicada, valores).
```

## Input/Output Examples

```http
POST /api/bookings/{id}/cancel
{ "reason":"CLIENT_REQUEST", "serviceStartsAt":"2026-07-01T10:00:00Z" }
200 OK
{ "bookingId":"b71...", "status":"CANCELLED", "policyType":"STANDARD",
  "charges": [ {"kind":"PENALTY","amount":{"amount":"50.00","currency":"BRL"},"costBearer":"AGENCY"} ] }
```

```http
# Venda ALL_SALES_FINAL com reembolso comercial ao cliente: DUAS obrigações
200 OK
{ "bookingId":"b88...", "status":"CANCELLED", "policyType":"ALL_SALES_FINAL",
  "charges": [
    {"kind":"SUPPLIER","amount":{"amount":"500.00","currency":"USD"},"costBearer":"ACME"},
    {"kind":"CUSTOMER_REFUND","amount":{"amount":"480.00","currency":"BRL"},"costBearer":"ACME"} ] }
```

## API Contracts

- `POST /api/bookings/{id}/cancel` — agora aceita `{reason, serviceStartsAt}` e retorna os **encargos**
  calculados (substitui o cancelamento simples da SPEC-0006). → 200 | 404 | 409.
- `POST /api/bookings/{id}/no-show` — aplica `NoShowPolicy`; aceita prova de voo cancelado opcional.
- `GET /api/products/{ref}/cancellation-policy` e `PUT ...` — administra a política por produto/
  fornecedor (fonte do snapshot). Autorização: papel administrativo.
- OpenAPI atualizada; enums (`type`, `kind`, `costBearer`) com valores externos explícitos.

## Events

- `CancellationCharged` — `{bookingId, charges[], policyType, occurredAt}`. Produtor: `booking`.
  Consumidores: `finance` (lança AP/AR), `payout` (reembolso), `intelligence`
  (exposição merchant em aberto, 8.2-G).
- `NoShowCharged` — `{bookingId, fee, waived, occurredAt}`.
- `MerchantObligationIncurred` — `{bookingId, supplierCharge, occurredAt}` no caso ALL_SALES_FINAL,
  para tornar **visível** a obrigação que não se anula (8.2-G/H).

## Persistence Changes

```txt
V10__create_cancellation.sql
  cancellation_policies(                       -- fonte (por produto/fornecedor)
    id uuid PK, scope_ref varchar not null,    -- produto/fornecedor (valor)
    type varchar not null, refundable boolean not null, cost_bearer varchar not null,
    windows_json jsonb not null,               -- [{hoursBefore, penaltyPct}]
    created_at, updated_at timestamptz not null, version bigint not null
  )
  booking_cancellation_snapshots(              -- congelado na confirmação (BR1)
    booking_id uuid PK, type varchar not null, refundable boolean not null, cost_bearer varchar not null,
    windows_json jsonb not null, no_show_fee numeric(18,2) null, waived_if_flight_cancelled boolean null
  )
  cancellation_charges(                         -- os fatos resultantes (BR5)
    id uuid PK, booking_id uuid not null, kind varchar not null,
    amount numeric(18,2) not null, currency varchar not null, cost_bearer varchar not null,
    created_at timestamptz not null, created_by varchar null
  )
```

`CancellationPolicy` é **policy como objeto** (enum com comportamento + janelas); o cálculo da multa é
método de domínio testável (datas/timezone sensíveis — `backend.md`).

## Validation Rules

- Delivery: `serviceStartsAt` obrigatório no cancel; prova de voo (documento) opcional no no-show.
- Domain: seleção da janela aplicável (BR2) e a **não-compensação** das obrigações (BR5) como
  invariantes; cálculo de horas-até-serviço sensível a timezone (UTC).
- Integração: a dispensa por voo cancelado exige documento conforme (Compliance).

## Error Behavior

`booking.not-found` → 404; `booking.transition.invalid` → 409 (cancelar fora de estado válido);
`cancellation.policy.invalid` → 400 (janelas malformadas). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar cancelamento/no-show com a política aplicada e os encargos (bookingId, costBearer, correlation
  id). Métricas: `cancellations_total{type}`, `merchant_obligations_total`, soma de multas.

## Tests Required

- **Unit/domain:** seleção de janela e cálculo de multa (vários `hoursBefore`); `ALL_SALES_FINAL`
  produz **duas** obrigações que não se anulam; `NoShowPolicy` com e sem dispensa.
- **Integração (Testcontainers):** cancelar STANDARD gera PENALTY; cancelar ALL_SALES_FINAL gera
  SUPPLIER + CUSTOMER_REFUND; no-show com prova dispensa fee.
- **Regressão:** garantir que reembolso ao cliente **não** zera a obrigação com o fornecedor
  (falha antes, passa depois) — a armadilha do merchant.

## Acceptance Criteria

- Cancelar dentro de uma janela de 50% cobra a multa correta com o `costBearer` certo.
- A venda `ALL_SALES_FINAL` cancelada com reembolso comercial registra **duas** obrigações distintas e
  publica `MerchantObligationIncurred`.
- No-show de carro cobra fee, dispensada com prova de voo cancelado.
- `./mvnw verify` verde.

## Open Questions

- **Q3 (merchant of record):** se o portal é merchant, ele **assume** a obrigação com a marketplace e o
  reembolso; se afiliado, não. Isso define **quem** é o `costBearer` no ALL_SALES_FINAL — **decisão de
  negócio em aberto**; o modelo suporta ambos, mas o default precisa ser confirmado.
- Multa em **moeda estrangeira** vs BRL (conversão pela taxa de quando?) — confirmar.

## Out of Scope

Execução do reembolso (SPEC-0017), chamado de pós-venda (SPEC-0018), lançamento contábil (SPEC-0015).
