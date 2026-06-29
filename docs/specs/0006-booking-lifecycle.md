# 0006 - Booking (Reserva)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções de projeto (dinheiro, datas, erros, auditoria, paginação, eventos in-process) herdadas
> da **SPEC-0001 §"Convenções do projeto"**. Aqui só o que é específico de Booking.

## Goal

Transformar uma cotação aceita (SPEC-0005) num **compromisso operacional** com ciclo de vida
explícito e localizador, e disparar, na confirmação, o **accrual das duas comissões** — fechando a
ponte entre a composição comercial e o lado financeiro/conciliação.

## Scope

**Em escopo:** criar uma `Booking` a partir de um `Quote`; máquina de estados
`QUOTED → ORDERED → PENDING → CONFIRMED → (CHANGED | CANCELLED | NO_SHOW) → COMPLETED`; localizador
**interno gerado** ou **externo digitado**; expiração automática de `PENDING` em 72h; cancelamento
**simples** (reverte as comissões); eventos de ciclo de vida; tela Angular de detalhe + ações.

**Fora de escopo:** `CancellationPolicy` rica (janelas de multa, `ALL_SALES_FINAL`, `costBearer`) e
`NoShowPolicy` detalhada — são a **SPEC-0010**; aqui o cancelamento é integral e sem multa. Reembolso
financeiro (Payout/AfterSales). Alteração com recomposição de preço (CHANGED apenas marca o estado).

## Business Context

Redesenho 7.4. A reserva é o "serviço a entregar" para Operações e a "obrigação a liquidar" para o
Financeiro — a mesma `Booking` vista por dois contextos. O localizador pode nascer **fora** (digitado
de um portal/voucher externo), porque o mundo é híbrido (Parte 3.3). A confirmação é o gatilho do
direito à comissão (accrual das duas pontas, 7.1).

## Business Rules

```txt
BR1  Toda Booking MUST referenciar um quoteId existente (validado via fachada do Quoting) e
     guardar o accountId daquele Quote. Quote inexistente => 404 booking.quote.not-found.
BR2  Estados e transições válidas (máquina de estados):
       QUOTED   → ORDERED
       ORDERED  → PENDING
       PENDING  → CONFIRMED | CANCELLED        (e expira sozinha — BR4)
       CONFIRMED→ CHANGED | CANCELLED | NO_SHOW | COMPLETED
       CHANGED  → CONFIRMED | CANCELLED
     Qualquer outra transição => 409 booking.transition.invalid.
BR3  Localizador: se origin = INTERNAL, o sistema gera um código único; se origin = EXTERNAL, o
     operador informa o código (não-vazio). Localizador MUST ser único por (origin, code).
     Duplicado => 409 booking.locator.duplicate.
BR4  Uma Booking em PENDING há mais de 72h MUST transicionar para CANCELLED automaticamente
     (motivo PENDING_TIMEOUT), revertendo comissões como em cancelamento normal.
BR5  Ao entrar em CONFIRMED, o sistema MUST publicar BookingConfirmed; ao entrar em CANCELLED
     (manual ou timeout), MUST publicar BookingCancelled. NO_SHOW publica BookingNoShow.
BR6  Transições importantes (CONFIRMED, CANCELLED, NO_SHOW, COMPLETED) MUST ser auditadas
     (quem, quando, de→para, motivo) — `delivery.md` (audit).
```

## Input/Output Examples

```http
POST /api/bookings
{ "quoteId": "0a3f...", "locator": { "origin": "EXTERNAL", "code": "ALAMO-7731QX" } }
201 Created
{ "id": "b71e...", "quoteId": "0a3f...", "accountId": "8f1c...", "status": "ORDERED",
  "locator": { "origin": "EXTERNAL", "code": "ALAMO-7731QX" }, "createdAt": "2026-06-26T13:00:00Z" }
```

```http
POST /api/bookings/{id}/confirm
200 OK  { "id": "b71e...", "status": "CONFIRMED", "confirmedAt": "2026-06-26T13:05:00Z" }
# efeito: publica BookingConfirmed -> Commissioning reage com ExpectedCommissionAccrued

POST /api/bookings/{id}/confirm   (estado COMPLETED)
409 Conflict { "code": "booking.transition.invalid", "message": "...", "fields": [] }
```

## API Contracts

- `POST /api/bookings` — cria a partir do `quoteId` (+ localizador). Estado inicial `ORDERED`
  (o `QUOTED` é o estágio do Quoting; ao "pedir", vira `ORDERED`). → 201.
- `POST /api/bookings/{id}/order|pending|confirm|cancel|no-show|complete|change` — ações de
  transição (REST de ação de domínio, permitido — `modules-and-apis.md`). `cancel` aceita `{reason}`.
  → 200 | 404 `booking.not-found` | 409 `booking.transition.invalid`.
- `GET /api/bookings/{id}` → 200 | 404. `GET /api/bookings?status=&accountId=&page=&size=` →
  `PageResponse`, sort default `createdAt desc`.
- OpenAPI atualizada; enums de status/origin com valores externos explícitos.

## Events

- `BookingConfirmed` — `{bookingId, quoteId, accountId, occurredAt}`. Produtor: `booking`.
  Consumidores: `commissioning` (accrual), `reconciliation` (abre caso). In-process; vira
  contrato/outbox ao cruzar serviço.
- `BookingCancelled` — `{bookingId, reason, occurredAt}`. Consumidores: `commissioning` (reversão),
  `reconciliation`.
- `BookingNoShow` — `{bookingId, occurredAt}`. Consumidor: `commissioning` (regra de no-show em 0010).
- `ExpectedCommissionAccrued` / `CommissionReversed` são **eventos do Commissioning** (7.1),
  publicados em reação aos acima — não os emita a partir de `booking`.

## Persistence Changes

```txt
V5__create_bookings.sql
  bookings(
    id uuid PK,
    quote_id uuid not null,                 -- valor, SEM FK cross-módulo (preserva extração)
    account_id uuid not null,               -- idem (copiado do Quote)
    status varchar not null,
    locator_origin varchar not null,        -- INTERNAL | EXTERNAL
    locator_code varchar not null,
    pending_since timestamptz null,         -- base do timeout de 72h (BR4)
    confirmed_at timestamptz null,
    cancel_reason varchar null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null,
    version bigint not null
  )
  UNIQUE INDEX ux_bookings_locator (locator_origin, locator_code)
```

`@Version` para concorrência nas transições. A máquina de estados é um **enum com comportamento**
(transições válidas) + exceção específica em transição inválida (`backend.md`).

## Validation Rules

- Delivery: Bean Validation no request; `reason` obrigatório no cancel.
- Application: existência do `Quote` via fachada do Quoting (BR1); existência da Booking nas ações.
- Domain: a máquina de estados rejeita transições inválidas (BR2) — invariante no agregado.
- Persistence: índice único do localizador (BR3) traduzido para `booking.locator.duplicate`.

## Error Behavior

`booking.quote.not-found` → 404; `booking.not-found` → 404; `booking.transition.invalid` → 409;
`booking.locator.duplicate` → 409. Chaves i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar cada transição auditável como evento de negócio (estado de→para, bookingId, quem,
  correlation id). Métrica `bookings_confirmed_total`, `bookings_pending_timeout_total`.
- O job de timeout (BR4) é um job com idempotência/locking/histórico (`messaging-and-integrations.md`).

## Tests Required

- **Unit/domain:** matriz de transições válidas/ inválidas da máquina de estados; geração de
  localizador interno único; aceitação de localizador externo.
- **Integração (Testcontainers):** criar→confirmar publica `BookingConfirmed`; cancelar publica
  `BookingCancelled`; transição inválida → 409; localizador duplicado → 409.
- **Job:** PENDING > 72h vira CANCELLED (com relógio controlado — datas testáveis, `backend.md`).
- **Regressão:** confirmar uma Booking COMPLETED deve falhar 409 (falha antes, passa depois).

## Acceptance Criteria

- Criar Booking a partir de um Quote válido retorna 201 em `ORDERED`.
- O fluxo `order→pending→confirm` chega a `CONFIRMED` e publica `BookingConfirmed`.
- Cancelar publica `BookingCancelled`; transições inválidas retornam 409.
- PENDING expira em 72h para CANCELLED via job.
- A tela mostra estado atual, histórico de transições e ações permitidas conforme o estado.
- `./mvnw verify` verde (ArchUnit/Modulith).

## Open Questions

- O `Quote` é consumido **uma vez** por Booking, ou um Quote pode gerar várias Bookings? (Assumido:
  1:1 no v1; confirmar com o dono.)
- `CHANGED` recompõe preço/comissão ou só remarca? Recomposição depende da fórmula de preço (Open
  Question da SPEC-0005) — **adiada**; aqui `CHANGED` só marca estado.

## Out of Scope

`CancellationPolicy`/`NoShowPolicy` ricas (SPEC-0010), reembolso (Payout/AfterSales), recomposição
de preço na alteração, parcelamento.
