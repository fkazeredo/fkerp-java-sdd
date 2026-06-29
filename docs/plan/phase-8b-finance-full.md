# Plano — Sub-fase 8b: Finance (SPEC-0015 full) — AP/AR automático por evento + balancete

> Modo autônomo (RUN-PHASE, FASE-ALVO=8, **escopo SPEC-0015 apenas**). **Estende** o seam de Fase 2
> (já existente: `LedgerEntry` PROVISIONAL→CONFIRMED→SETTLED, `AccountingPeriod` OPEN→CLOSING→CLOSED,
> `closePeriod`+`CloseGuard` veto, `LedgerDirectory`, eventos, V7, API manual, i18n) **sem quebrar
> nenhum contrato público** — o veto do Compliance e todos os testes atuais ficam verdes. Finance é
> **subdomínio genérico**: a entrega é o **livro-caixa operacional** (seam), **não** um Razão contábil
> pleno (DL-0042). Nada de plano de contas/partidas dobradas/DRE/SPED.

## Objetivo

Cumprir o que faltava da SPEC-0015 sobre o seam mínimo:

1. **BR5 — eventos de negócio viram lançamentos, idempotente.** Finance consome os eventos **já
   publicados e órfãos de lançamento** do Booking (SPEC-0010): `CancellationCharged`, `NoShowCharged`,
   `MerchantObligationIncurred`, e posta AP/AR automaticamente (PROVISIONAL), **uma vez** por fato
   (UNIQUE `(source_ref, charge_kind)` + state-check). **Não** se inventa produtor para comissão/
   liquidação (DL-0041); fica como seam diferido, sem dívida.
2. **Relatório de período / balancete** por moeda e por status (`GET /periods/{yyyymm}/trial-balance`)
   — DL-0043, livro-caixa operacional, sem contabilidade plena.
3. **Decisão comprar-vs-construir reafirmada** explicitamente para o escopo "full" (DL-0042).
4. **Regressão de ouro intacta:** lançamento sem documento **bloqueia** o fechamento; com documento,
   fecha (`CloseVetoIntegrationTest`). O lançamento automático entra no mesmo veto.

## Decisões registradas antes do código (decision-log)

| DL | Lacuna | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0041 | Quais eventos viram lançamento; mapa; período; idempotência | Consome `CancellationCharged`/`NoShowCharged`/`MerchantObligationIncurred` (booking); mapa direção/tipo/party; período = `YYYY-MM` de `occurredAt` UTC; UNIQUE `(source_ref, charge_kind)` + pré-check; comissão/SupplierSettlement **diferidos** (sem produtor) | Média | Moderada |
| DL-0042 | "Full" = construir GL pleno? | **Não**: livro-caixa operacional; GL pleno (plano de contas/partidas dobradas/DRE/SPED) = comprar/integrar | Alta | Moderada |
| DL-0043 | Forma do relatório de período | `GET /periods/{yyyymm}/trial-balance` por moeda e status (net operacional = AR−AP); aditivo, não muda `GET /periods` | Alta | Barata |

## Fronteira / Spring Modulith (acíclico)

- Finance passa a depender de `domain.booking` (tipos de evento consumidos). **Booking não depende de
  Finance** (grep confirma) → sem ciclo. **Compliance depende de Finance** (`LedgerDirectory`,
  `CloseGuard`) → cadeia `compliance → finance → booking`, **acíclica**.
- Finance é **consumidor-folha** do Booking: **nunca** chama o repositório/serviço do Booking de volta
  — só lê o evento in-process (mesmo padrão de `reconciliation`/`intelligence`).
- O listener vive em `finance.internal` (module-private); `@EventListener` síncrono na transação do
  produtor (consistência transacional do lançamento com o fato).

## Fatias (ordem de dependência)

### Slice 8b-1 — Lançamento automático de AP/AR por evento (idempotente) · `feature/slice-8b1-finance-event-posting`
- **Teste vermelho (integração, Testcontainers):**
  - cancelar uma reserva ALL_SALES_FINAL (gera `CancellationCharged` com PENALTY+SUPPLIER/REFUND e
    `MerchantObligationIncurred`) → aparecem lançamentos AP/AR no período do `occurredAt`, com o mapa
    do DL-0041, e o PAYABLE do fornecedor **não duplica** (merchant trap preservado: PAYABLE
    fornecedor e PAYABLE/REFUND cliente coexistem, DL-0024).
  - **idempotência provada:** republicar o mesmo evento (ou reprocessar) **não** cria segundo
    lançamento (conta lançamentos antes/depois).
  - no-show com fee aplicada → 1 RECEIVABLE PENALTY; no-show waived → **nenhum** lançamento.
- **Esqueleto → verde:**
  - migração **V19** `posted_event_entries(source_ref varchar, charge_kind varchar, entry_id uuid,
    created_at, UNIQUE(source_ref, charge_kind))`.
  - `finance.internal` entidade/record de dedupe + repositório module-private.
  - `FinanceService.postFromCharge(sourceRef, chargeKind, direction, party, money, entryType,
    occurredAt)` — pré-check de dedupe, deriva período de `occurredAt` (UTC), reusa o caminho de
    `register` (lazy-open, PROVISIONAL, publica `LedgerEntryRegistered`), grava a linha de dedupe na
    mesma transação; **no-op** se já existe ou se viola a UNIQUE (corrida) ou se período CLOSED (log).
  - `BookingChargeEventsListener` (`finance.internal`): mapeia cada evento → chamadas `postFromCharge`.
- **Portões:** `spotless:apply` → `./mvnw verify` (ArchUnit + Modulith acíclico + Checkstyle).
- **DoD:** spec BR5/Validation movidos para coberto; MANUAL.md (lançamento automático); test-report.

### Slice 8b-2 — Balancete do período (trial-balance por moeda/status) · `feature/slice-8b2-finance-trial-balance`
- **Teste vermelho (integração):** lançar AP/AR em moedas/status variados num período →
  `GET /periods/{yyyymm}/trial-balance` devolve totais **por moeda** e **por status**, `net = AR−AP`
  por moeda, contagens; nunca soma moedas distintas (DL-0013).
- **Esqueleto → verde:** `TrialBalanceView` (DTO público), query de agregação por (moeda, direção,
  status) no repositório (leitura), método `FinanceService.trialBalance(periodId)`, endpoint no
  `FinanceController`. Sem migração.
- **Portões + DoD:** OpenAPI, MANUAL.md (consultar balancete), test-report, `./mvnw verify` verde.

## Testes (proporcionais)
- **Integração:** posting idempotente (cancelamento/merchant/no-show), balancete por moeda/status.
- **Regressão:** `CloseVetoIntegrationTest` e `FinanceIntegrationTest` herdados **continuam verdes**
  (contratos públicos intactos); o lançamento automático entra no veto como qualquer outro.
- **Arquitetura:** Modulith `verify()` acíclico com a nova dependência `finance → booking`.

## Fora de escopo (registrado)
- Plano de contas, partidas dobradas, DRE, SPED/ECD → comprar/integrar (DL-0042, BR7).
- Accrual de comissão na confirmação (`ExpectedCommissionAccrued`) e liquidação ao fornecedor da
  Reconciliation (`SupplierSettlement`): **eventos não publicados hoje** → seam diferido (DL-0041),
  sem inventar produtor.

## Entregáveis
- Plano (este arquivo), DL-0041/0042/0043 + INDEX, caderno de testes 8b-1/8b-2 + INDEX, MANUAL.md,
  OpenAPI, release note `0.10.0` (próximo MINOR após 0.9.0, ADR 0015), bump `backend/pom.xml`.
