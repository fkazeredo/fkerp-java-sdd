# Plano da Fase 8e — AfterSales (SPEC-0018)

> Modo autônomo (RUN-PHASE). FASE-ALVO = 8, **escopo SPEC-0018 apenas**. Implementa o
> contexto de pós-venda: chamados (state machine), alteração/cancelamento e reembolso
> (orquestrando os donos), SLA (com detecção de violação por job de relógio controlado) e
> custo de servir. Versão alvo: **0.13.0** (próximo MINOR após 0.12.0, ADR 0015).

## Contexto e fronteiras

Novo módulo `com.fksoft.domain.aftersales` (15º módulo Modulith). Agregado `SupportCase`.
AfterSales **orquestra, não reimplementa**:

- **SLA** = parâmetro governado → resolve via `CommercialPolicy` (SPEC-0014, motor de
  precedência). Chaves: `AFTERSALES_SLA_FIRST_RESPONSE` (24h), `AFTERSALES_SLA_RESOLUTION`
  (72h), `AFTERSALES_SLA_REFUND` (48h) — seed SYSTEM_DEFAULT (DL-0052).
- **Cancelamento** (`CANCELLATION_REQUEST` resolvido) → `BookingService.cancel(...)` (SPEC-0010);
  AfterSales não calcula multa nem muda estado da reserva (BR2).
- **Reembolso** (`REFUND_REQUEST` aprovado) → `PayoutService.create(kind=REFUND, originRef)`
  (SPEC-0017); idempotente (não cria 2 Payouts pro mesmo refund); a **armadilha do merchant**
  fica intacta (DL-0024/DL-0051): o reembolso ao cliente nunca cancela a obrigação do fornecedor.
- **SLA breach** = job de relógio controlado que marca `BREACHED` e publica `SlaBreached`
  (alerta, não bloqueia).
- **Custo de servir** acumula por caso (Money, scale 2 HALF_UP) e alimenta a Intelligence.

`aftersales` depende de: `commercialpolicy` (resolve), `booking` (cancel facade), `payout`
(create facade), `money`/`error` kernels. Não depende de Finance/Compliance. Os módulos donos
**não** dependem de `aftersales` → grafo Modulith permanece **acíclico**.

## Fatias (uma feature branch por fatia → merge --no-ff em develop → push)

### 8e-1 — SupportCase + state machine (V23)
- Agregado `SupportCase` (`internal`): id, bookingId (valor), type, status, summary, openedAt,
  dueAt, resolvedAt, resolution, costToServe (jsonb), linkedPayoutId, reopenCount, audit, version.
- `SupportCaseType` (COMPLAINT | CHANGE_REQUEST | CANCELLATION_REQUEST | REFUND_REQUEST | INFO).
- `SupportCaseStatus` enum-com-comportamento + máquina de estado:
  OPEN → IN_PROGRESS → WAITING → (IN_PROGRESS) → RESOLVED → CLOSED; transições inválidas
  lançam `SupportCaseTransitionInvalidException` (409).
- `AfterSalesService` (open/assign/progress/wait/resolve/close/get/list); controller REST;
  migração `V23__create_aftersales.sql`; i18n (pt-BR + fallback); `HttpErrorMapping`.
- Testes: unit da máquina (válidas + inválidas) + integração (persistência, abrir/transitar).

### 8e-2 — SLA de CommercialPolicy + detecção de breach (relógio controlado)
- `dueAt` no `open` derivado das chaves SLA resolvidas via `CommercialPolicyService.resolve`
  (NUMBER = horas). Primeira resposta governa o `firstResponseDueAt`; resolução/reembolso
  governam o `dueAt` por tipo.
- `markBreaches(now)` no service (assinatura recebe o instante, como `expirePendingBookings(cutoff)`
  do Booking) → marca `BREACHED` e publica `SlaBreached`; **não bloqueia**. Scheduler em
  `infra.jobs` injeta o `Clock`.
- Testes: dentro-do-SLA × estourado (first-response/resolution/refund) com instante controlado;
  override de Diretiva muda o SLA efetivo (prova de resolução por política).

### 8e-3 — Resolve: orquestração (cancel/refund) + custo de servir
- `resolve(REFUND_APPROVED, amount)` → `PayoutService.create(REFUND, originRef=caseId)` uma vez
  (idempotente: 2ª chamada não cria outro Payout); guarda `linkedPayoutId`.
- `resolve(CANCEL_APPROVED, ...)` → `BookingService.cancel(bookingId, ...)`; AfterSales não muda
  estado da reserva diretamente (BR2).
- Custo de servir acumula (esforço + reembolso associado) ao fechar (BR5); eventos
  `SupportCaseOpened`/`SupportCaseResolved`/`SlaBreached`.
- Regressão merchant-trap: caso de reembolso resolvido aciona Payout exatamente uma vez e a
  PAYABLE do fornecedor permanece intacta (verde).

## Portões (cada fatia)
`./mvnw spotless:apply` → `./mvnw verify` (ArchUnit + Spring Modulith **acíclico** + Spotless +
Checkstyle) verde com Docker no ar. Nunca afrouxar gate. Caderno de testes antes do merge.

## Entregáveis finais
- Plano (este arquivo); caderno `docs/test-report/8e-*.md` + INDEX.
- DL-0052..DL-0054 (+ INDEX atualizado).
- `V23` migração; OpenAPI atualizada (controller anotado).
- Release note `docs/release-notes/0.13.0.md`; tag `0.13.0` (main+develop).
