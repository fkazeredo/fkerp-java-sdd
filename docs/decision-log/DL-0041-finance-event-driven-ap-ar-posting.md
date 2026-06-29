# DL-0041 — Finance: lançamento automático de AP/AR a partir de eventos de negócio (idempotente)

- **Fase:** 8b (Finance — SPEC-0015 full)
- **Spec(s):** SPEC-0015 (BR5 "Eventos de negócio viram lançamentos"; Validation Rules
  "idempotência no consumo de eventos"; Scope "consumo de eventos que geram lançamentos")
- **ADR relacionado:** 0011, 0012 ; `architecture/messaging-and-integrations.md` (Idempotency:
  "Use database constraints and state checks before building complex idempotency infrastructure")
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0015 (BR5) manda **eventos de negócio virarem lançamentos** AP/AR, mas o seam da Fatia 2 só
expõe a API manual (`POST /entries`) e o veto de fechamento — **nenhum** evento é consumido pelo
Finance hoje. Faltava decidir: (a) **quais** eventos já publicados (e ainda sem consumidor) viram
lançamento; (b) o **mapa** evento → `direction`/`entryType`/`party`; (c) como derivar o **período**;
(d) a **chave de idempotência** que prova "re-entregar o mesmo evento não duplica o lançamento".

## Decisão

**1. Consumir apenas eventos já publicados e sem consumidor de lançamento — sem inventar produtor.**
Os eventos do `booking` (SPEC-0010) `CancellationCharged`, `NoShowCharged` e
`MerchantObligationIncurred` já são publicados in-process e **ninguém** os transforma em AP/AR. O
Finance passa a consumi-los e a postar os lançamentos correspondentes. **NÃO** se consome
`ExpectedCommissionAccrued` nem `SupplierSettlement`: esses **não existem como evento publicado** em
nenhum módulo hoje (só aparecem na prosa das specs/OVERVIEW). Criar produtor para eles seria inventar
fluxo fora de escopo (Rule Zero / `simulation-and-mocking.md`). Fica **diferido** (ver Impacto), com
o seam pronto (basta um novo listener idempotente quando o evento existir — p.ex. quando Booking
publicar accrual de comissão na confirmação, ou Reconciliation publicar a liquidação ao fornecedor).

**2. Mapa evento → lançamento** (Money na moeda original do encargo — DL-0013; escala 2 HALF_UP):

| Evento (booking) | Encargo (`ChargeKind`) | `direction` | `entryType` | `party` |
|---|---|---|---|---|
| `CancellationCharged` | `PENALTY` | RECEIVABLE | `PENALTY` | AGENCY (`bookingId`) |
| `CancellationCharged` | `CUSTOMER_REFUND` | PAYABLE | `REFUND` | AGENCY (`bookingId`) |
| `CancellationCharged` | `SUPPLIER` | PAYABLE | `SUPPLIER_SETTLEMENT` | SUPPLIER (`bookingId`) |
| `MerchantObligationIncurred` | `SUPPLIER` | PAYABLE | `SUPPLIER_SETTLEMENT` | SUPPLIER (`bookingId`) |
| `NoShowCharged` (fee≠null, !waived) | `NO_SHOW`→ | RECEIVABLE | `PENALTY` | AGENCY (`bookingId`) |

  - O `SUPPLIER` de `CancellationCharged` e o de `MerchantObligationIncurred` referem-se ao **mesmo
    fato** (a obrigação irrecuperável do ALL_SALES_FINAL é publicada nas duas formas). Para **não
    duplicar**, o Finance posta o lançamento de fornecedor **apenas** ao consumir
    `MerchantObligationIncurred` e **ignora** o encargo `SUPPLIER` dentro de `CancellationCharged`
    (a chave de idempotência por `(bookingId, SUPPLIER)` também o impediria, mas a regra é explícita
    para clareza). Assim, o "merchant trap" (DL-0024: encargos não se compensam) é **preservado**: o
    PAYABLE do fornecedor e o PAYABLE/REFUND ao cliente coexistem como lançamentos distintos.
  - `party.id` = `bookingId.toString()` (valor — referência a outro contexto por id, nunca FK;
    modules-and-apis.md). Não há, nesta fase, o id comercial da agência/fornecedor no evento; o
    `bookingId` é a referência rastreável disponível. Marcado como ponto a enriquecer quando os
    eventos carregarem `accountId`/`supplierId`.

**3. Período** = `YYYY-MM` derivado de `event.occurredAt()` em **UTC** (`YearMonth.from(occurredAt
.atZone(UTC))`). É o instante do fato; o lançamento nasce **PROVISIONAL** no período aberto
correspondente (criado lazy se não existir — reusa `FinanceService.register`). Se o período já
estiver **CLOSED**, o consumo **não** força lançamento no período fechado (BR4): registra log/métrica
de rejeição e descarta (o ajuste manual vai para período aberto). Como o consumo roda **na transação
do produtor** (in-process `@EventListener` síncrono, como `reconciliation`/`intelligence`), isso só
ocorreria se o fato de negócio caísse num mês já fechado — caso de borda raro e auditável.

**4. Idempotência por chave de origem (state-check + UNIQUE).** Nova tabela `posted_event_entries`
com **UNIQUE (source_ref, charge_kind)** onde `source_ref = bookingId` e `charge_kind` = o tipo do
encargo postado. Antes de postar, o consumer faz **pré-checagem de existência**; ao postar, grava a
linha de dedupe **na mesma transação** do lançamento. Uma **re-entrega do mesmo evento** encontra a
linha (pré-check) ou viola a UNIQUE (corrida) → **no-op**, sem segundo lançamento. É o padrão pedido
por `messaging-and-integrations.md` (constraint + state check antes de infra de idempotência
complexa) e o mesmo espírito do `(sourceRef, periodRef)` único do People (BR5/SPEC-0012).

## Justificativa

- **BR5 + Validation Rules da SPEC-0015** pedem exatamente isto, com idempotência. Os eventos do
  Booking são os **únicos** candidatos reais (publicados e órfãos de lançamento) — `git grep`
  confirma que `ExpectedCommissionAccrued`/`SupplierSettlement` não são publicados.
- **Acíclico (Spring Modulith):** Finance passa a depender de `domain.booking` (tipos de evento).
  Booking **não** depende de Finance nem de Compliance (verificado por grep); Compliance depende de
  Finance. Logo `compliance → finance → booking` é uma cadeia **sem ciclo**. Finance nunca chama de
  volta o repositório/serviço do Booking — só lê o evento (consumidor-folha do Booking).
- **Sem quebrar o seam:** os contratos públicos (`FinanceService.register/confirm/closePeriod`,
  `LedgerDirectory`, `CloseGuard`, eventos) ficam **intactos**; o veto do Compliance continua válido
  (os lançamentos automáticos entram como qualquer outro lançamento PROVISIONAL e podem exigir
  documento). Regressão `CloseVetoIntegrationTest` permanece verde.

## Alternativas descartadas

- **Criar `ExpectedCommissionAccrued`/`SupplierSettlement` e produzi-los no Booking/Reconciliation.**
  Descartada: inventaria fluxo/produtor fora do escopo desta spec (Rule Zero); muda contratos de
  outros módulos sem spec dona. Fica como seam diferido.
- **Netar o encargo de fornecedor (publicado em dose dupla) somando/subtraindo.** Descartada: violaria
  DL-0024 (encargos nunca se compensam). A solução é **postar uma vez** (via `MerchantObligationIncurred`)
  e ignorar a cópia, não netar.
- **Tabela de inbox genérica com `eventId` + `@ApplicationModuleListener` (event publication
  registry).** Descartada nesta fatia: os eventos in-process não carregam `eventId` estável e o
  registry persistente do Modulith é infra maior que o problema (Rule Zero). A UNIQUE de negócio
  `(source_ref, charge_kind)` é suficiente e mais clara.
- **Derivar o período do `now()` do clock em vez do `occurredAt`.** Descartada: o lançamento pertence
  ao mês **do fato**, não ao do processamento; usar `occurredAt` é o correto para o fechamento.

## Impacto

- **Novo:** listener module-internal `BookingChargeEventsListener` em `finance.internal`; tabela de
  dedupe `posted_event_entries` (migração **V19**); método `FinanceService.postFromCharge(...)`
  idempotente; métrica `ledger_entries_total{direction,type}` já coberta pelos lançamentos.
- **Diferido (seam pronto, sem dívida):** accrual de comissão na confirmação de reserva
  (`ExpectedCommissionAccrued`) e liquidação ao fornecedor da Reconciliation
  (`SupplierSettlement`) — registrados aqui como pendência; quando o produtor existir, adiciona-se um
  listener idempotente análogo, sem mudar o seam.
- **Contratos:** sem mudança no JSON público; a API manual segue idêntica. Novos lançamentos
  aparecem em `GET /entries` como quaisquer outros.

## Como reverter

Reversão **moderada e contida no módulo**: remover o `BookingChargeEventsListener` e a tabela
`posted_event_entries` (nova migração que dropa). Os lançamentos já postados permanecem como
lançamentos manuais comuns. Nenhum contrato público muda, então o raio é o `finance` apenas.
