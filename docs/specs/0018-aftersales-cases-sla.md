# 0018 - AfterSales (Chamados, Alteração, Reembolso e SLA)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. **Orquestra**, não reimplementa: cancelamento/multa são da
> SPEC-0010 (Booking/Cancellation), o reembolso é executado pela SPEC-0017 (Payout). Os sinais de
> custo de servir alimentam a **Intelligence (SPEC-0013)**.

## Goal

Dar à operação um **contexto de pós-venda** que registra **chamados**, conduz **alterações** e
**cancelamentos/reembolsos** (delegando aos donos), e mede **SLA** — gerando o "custo de servir" que o
DSS usa para achar produto/fornecedor que "vende bonito, dá prejuízo" (redesenho linha 156, 298, 300, 322).

## Scope

**Em escopo:** o agregado `SupportCase` (tipo COMPLAINT | CHANGE_REQUEST | CANCELLATION_REQUEST |
REFUND_REQUEST | INFO; reserva referenciada; status OPEN/IN_PROGRESS/WAITING/RESOLVED/CLOSED; prazos de
SLA); a **orquestração**: um `CANCELLATION_REQUEST` aciona o cancelamento na Booking (SPEC-0010), um
`REFUND_REQUEST` aciona um `Payout` REFUND (SPEC-0017) referenciando a origem; medição de SLA
(due/breached) e registro de retrabalho por reserva/produto/fornecedor.

**Fora de escopo:** a **política de multa/cancelamento** (SPEC-0010) e a **execução do pagamento**
(SPEC-0017) — aqui só se **decide e encaminha**; canais externos (e-mail/WhatsApp) entram como ACL depois.

## Business Context

O pós-venda é onde a margem **vaza** (retrabalho, reembolso, fornecedor ruim). Modelá-lo como contexto
próprio permite **atribuir custo** ("margem real = spread − custo de servir") e gerar os sinais
prescritivos: produto de alto AfterSales para repactuar/descontinuar; fornecedor de alto cancelamento
para renegociar SLA ou substituir (8.2-A/E).

## Business Rules

```txt
BR1  SupportCase MUST referenciar uma Booking (valor) e ter type, status e openedAt; prazos de SLA
     derivam do type/política (parâmetro governado — CommercialPolicy).
BR2  CANCELLATION_REQUEST resolvido como "cancelar" MUST acionar a Booking.cancel (SPEC-0010); o
     AfterSales NÃO calcula multa nem muda estado da reserva diretamente.
BR3  REFUND_REQUEST aprovado MUST criar um Payout REFUND (SPEC-0017) referenciando a obrigação de
     origem (CancellationCharge / este caso) — sem reembolso "solto".
BR4  SLA: cada caso tem dueAt; quando now > dueAt e não resolvido, marca BREACHED e publica
     SlaBreached (alerta — não bloqueia).
BR5  Ao fechar, o caso MUST registrar o **esforço/custo de servir** (tempo, reaberturas, reembolso
     associado) para a Intelligence atribuir margem real.
BR6  AfterSales MUST NOT reverter comissões nem lançar financeiro por conta própria — isso decorre dos
     eventos dos donos (Cancellation/Payout/Finance).
```

## Input/Output Examples

```http
POST /api/aftersales/cases
{ "bookingId":"b71...", "type":"REFUND_REQUEST", "summary":"voo cancelado pela cia" }
201 Created  { "id":"c90...", "status":"OPEN", "dueAt":"2026-06-28T13:00:00Z" }

POST /api/aftersales/cases/{id}/resolve
{ "resolution":"REFUND_APPROVED", "amount":{"amount":"480.00","currency":"BRL"} }
200 OK  { "id":"c90...", "status":"RESOLVED", "payoutId":"p61..." }   # encaminhou ao Payout
```

## API Contracts

- `POST /api/aftersales/cases` — abre chamado → 201.
- `POST /api/aftersales/cases/{id}/assign|progress|wait|resolve|close` — transições; `resolve` aceita a
  resolução (que pode acionar Booking.cancel e/ou Payout REFUND). → 200 | 404 | 409.
- `GET /api/aftersales/cases/{id}` → 200 | 404 `aftersales.case.not-found`.
- `GET /api/aftersales/cases?type=&status=&bookingId=&breached=&page=&size=` → `PageResponse`.
- OpenAPI atualizada.

## Events

- `SupportCaseOpened` / `SupportCaseResolved` — `{caseId, bookingId, type, occurredAt}`. Produtor:
  `aftersales`. Consumidor: `intelligence` (custo de servir, sinais de produto/fornecedor).
- `SlaBreached` — `{caseId, dueAt, occurredAt}` (alerta). Consumidor: `intelligence`, notificação.

## Persistence Changes

```txt
V18__create_aftersales.sql
  support_cases(
    id uuid PK, booking_id uuid not null, type varchar not null, status varchar not null,
    summary varchar null, opened_at timestamptz not null, due_at timestamptz null,
    resolved_at timestamptz null, resolution varchar null,
    cost_to_serve_json jsonb null,            -- esforço/reaberturas/reembolso p/ Intelligence (BR5)
    linked_payout_id uuid null, reopen_count int not null default 0,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
```

Orquestração chama as **fachadas** de Booking/Payout (sem FK, sem reimplementar regra). SLA é avaliado
por **job** (idempotência/locking) que marca `BREACHED`.

## Validation Rules

- Application: existência da Booking/caso; encaminhamentos idempotentes (não cria 2 Payouts pro mesmo
  refund).
- Domain: máquina de status do caso; SLA (BR4); registro de custo de servir (BR5).
- Princípio: AfterSales não muda estado de reserva nem financeiro por conta própria (BR2/BR6).

## Error Behavior

`aftersales.case.not-found` → 404; `aftersales.case.transition.invalid` → 409;
`aftersales.refund.duplicate` → 409. i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar abertura/resolução/SLA como evento de negócio (caseId, type, bookingId, correlation id).
  Métricas: `support_cases_total{type}`, `sla_breached_total`, tempo médio de resolução, reembolsos por
  fornecedor/produto (insumo do DSS).

## Tests Required

- **Unit/domain:** máquina de status; cálculo de `dueAt`/breach; registro de custo de servir.
- **Integração (Testcontainers):** `CANCELLATION_REQUEST` resolvido aciona Booking.cancel (fachada
  fake/real); `REFUND_REQUEST` aprovado cria um Payout REFUND idempotente; SLA estourado publica
  `SlaBreached`.
- **Regressão:** AfterSales **não** reverte comissão nem lança financeiro diretamente (falha antes,
  passa depois) — a reversão vem dos eventos dos donos.

## Acceptance Criteria

- Abrir um chamado de reembolso e aprová-lo cria um Payout REFUND referenciando a origem.
- Um chamado de cancelamento resolvido aciona a Booking (que aplica a política de multa da SPEC-0010).
- SLA estourado alerta sem travar a operação.
- `./mvnw verify` verde.

## Open Questions

- **Canais de atendimento** (e-mail/WhatsApp/portal) e se entram como ACL de entrada — em aberto.
- **Política de SLA** por tipo de caso (prazos) — parâmetro governado a definir (CommercialPolicy).
- Modelo de **atribuição de custo de servir** à margem real (quais custos contam) — confirmar com o dono.

## Out of Scope

Política de multa/cancelamento (SPEC-0010), execução de pagamento/reembolso (SPEC-0017), canais externos.
