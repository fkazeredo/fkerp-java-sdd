# 0011 - Exchange: Exposição, Subsídio × Drift e Posição do Livro

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Estende o `Exchange` da SPEC-0003 (que só tinha a **taxa
> congelada**) com a **taxa de mercado** e a **posição de risco** (redesenho 7.2). Feed externo segue
> a ACL/resiliência de `messaging-and-integrations.md`.

## Goal

Tornar **mensurável** o efeito do câmbio congelado: separar o gap em **subsídio (custo de promoção
consciente)** e **drift (risco de mercado)**, manter a **posição agregada do livro** (`ExchangeExposure`)
e entregar os primeiros relatórios de câmbio (`PromoFxResult`, `LiveExposure`). Só é possível porque o
`Exchange` é dono das **duas pontas**: a taxa servida × a taxa real da liquidação (redesenho 7.2).

## Scope

**Em escopo:** ingestão da **taxa de mercado** por par (série temporal, via porta `MarketRateProvider`
+ registro manual de contingência); abertura de uma `FxPosition` quando uma venda de **custo em moeda
estrangeira** é confirmada à taxa congelada; **accrual do subsídio** na abertura; **drift** marcado a
mercado enquanto aberta; fechamento quando a liquidação é registrada (SPEC-0007); o agregado
`ExchangeExposure`; os read-models `PromoFxResult(período)` e `LiveExposure` com alerta de drift.

**Fora de escopo:** o aconselhamento prescritivo (`PromoFxAdvisor`, `Counterfactual`, ROI da promo) é
**Intelligence (SPEC-0013)** — aqui só os fatos/relatórios descritivos. A taxa **congelada** e seu
histórico continuam na SPEC-0003.

## Business Context

A Acme vende em reais hoje e paga o fornecedor em dólar depois → **exposição** (lucro/risco pelo tempo).
O diretor congela uma taxa única de venda e, para ele, congelar é **promoção** (taxa melhor que a real
para atrair volume). Decompor o gap muda o relatório: *quanto se gastou de propósito (subsídio) e quanto
foi o mercado se mexendo (drift)*. Exemplo canônico (7.2): USD 1.000, congelada 5,40, mercado no freeze
5,55, na liquidação 5,70 → subsídio R$ 150, drift R$ 150, gap R$ 300.

## Business Rules

```txt
BR1  MarketRate(pair, rate scale 6 >0, observedAt) é série temporal append-only; "mercado agora" é a
     observação mais recente ≤ now. Origem: feed externo (porta) ou registro manual de contingência.
BR2  Ao confirmar uma venda com custo em moeda estrangeira precificada à taxa congelada, o sistema
     MUST abrir uma FxPosition { bookingId, foreignAmount, currency, pinnedRate, marketAtFreeze, OPEN }
     onde marketAtFreeze = MarketRate vigente no momento da composição/confirmação.
BR3  subsidy (accrual na abertura) = (marketAtFreeze − pinnedRate) × foreignAmount. Positivo = subsídio
     dado (promo); pode ser negativo (vendeu acima do mercado). Publica RateSubsidyAccrued.
BR4  Enquanto OPEN: drift(marcado a mercado) = (marketNow − marketAtFreeze) × foreignAmount. Quando o
     drift cruza o limite configurado, publica BookPositionDrifted (alerta — NÃO bloqueia).
BR5  Ao registrar a liquidação (SPEC-0007 fornece supplierSettlementRate), a FxPosition fecha:
     realizedDrift = (settlementRate − marketAtFreeze) × foreignAmount;
     totalGap = subsidy + realizedDrift  ( == (settlementRate − pinnedRate) × foreignAmount ).
BR6  ExchangeExposure (agregado do livro) = Σ posições OPEN de (subsidy + driftAtual). É leitura/
     projeção; MUST NOT alterar posições.
BR7  Todos os números guardam proveniência (qual taxa congelada, qual marketAtFreeze, qual liquidação).
```

## Input/Output Examples

```http
GET /api/exchange/exposure
200 OK
{ "asOf":"2026-06-26T15:00:00Z",
  "openPositions": 12, "currency":"BRL",
  "accruedSubsidy":{"amount":"1800.00","currency":"BRL"},
  "markToMarketDrift":{"amount":"950.00","currency":"BRL"},
  "totalExposure":{"amount":"2750.00","currency":"BRL"} }

GET /api/exchange/reports/promo-fx?period=2026-06
200 OK
{ "period":"2026-06",
  "subsidy":{"amount":"150.00","currency":"BRL"},      # intencional
  "drift":{"amount":"150.00","currency":"BRL"},        # risco
  "totalGap":{"amount":"300.00","currency":"BRL"} }    # exemplo 7.2
```

## API Contracts

- `POST /api/exchange/market-rates` — registro manual de taxa de mercado (contingência) → 201.
  (O caminho normal é o feed via porta `MarketRateProvider`, em job com resiliência.)
- `GET /api/exchange/exposure` → `LiveExposure` (posição agregada + drift + alerta).
- `GET /api/exchange/reports/promo-fx?period=YYYY-MM` → `PromoFxResult` (subsídio × drift × gap).
- `GET /api/exchange/positions/{bookingId}` → posição e sua decomposição → 200 | 404.
- OpenAPI atualizada.

## Events

- `RateSubsidyAccrued` — `{bookingId, subsidy, marketAtFreeze, pinnedRate, occurredAt}`. Produtor:
  `exchange`. Consumidor: `intelligence` (ROI da promo, 8.2-C).
- `BookPositionDrifted` — `{asOf, markToMarketDrift, threshold, occurredAt}` (alerta). Consumidor:
  `intelligence` (LiveExposure/alerta).
- `FxPositionClosed` — `{bookingId, subsidy, realizedDrift, totalGap, occurredAt}`. Consumidor:
  `intelligence`, `reconciliation` (confere o per-case).

## Persistence Changes

```txt
V11__create_exchange_exposure.sql
  market_rates(
    id uuid PK, currency_pair varchar not null, rate numeric(18,6) not null, observed_at timestamptz not null,
    source varchar not null,                 -- FEED | MANUAL
    created_at timestamptz not null,
    INDEX ix_market_rates_pair_observed (currency_pair, observed_at)
  )
  fx_positions(
    id uuid PK, booking_id uuid not null UNIQUE,
    foreign_amount numeric(18,2) not null, currency varchar not null,
    pinned_rate numeric(18,6) not null, market_at_freeze numeric(18,6) not null,
    subsidy_brl numeric(18,2) not null,                  -- accrual na abertura
    settlement_rate numeric(18,6) null, realized_drift_brl numeric(18,2) null, total_gap_brl numeric(18,2) null,
    status varchar not null,                              -- OPEN | CLOSED
    created_at, updated_at timestamptz not null, version bigint not null
  )
```

`ExchangeExposure`, `LiveExposure` e `PromoFxResult` são **read-models/projeções** (não force pelo
agregado — `persistence.md`). O feed de mercado é **job com idempotência/locking** e a tradução do
provedor é ACL (vendor DTO não vaza).

## Validation Rules

- Integração: feed com timeout/retry/circuit breaker; resposta validada (taxa > 0, par conhecido).
- Domain: cálculos de `subsidy`/`drift`/`totalGap` (BR3–BR5) como invariantes testáveis (escala 6,
  sinais corretos); abertura idempotente por `bookingId`.
- Application: limite de drift (BR4) é parâmetro governado (SPEC-0014).

## Error Behavior

`exchange.market-rate.not-found` → 404 (sem observação para o par); `exchange.position.not-found` → 404;
`exchange.rate.invalid` → 400 (taxa ≤ 0). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar accrual de subsídio, cruzamento de limite de drift e fechamento de posição como eventos de
  negócio (bookingId, valores, correlation id). Métricas: `fx_open_positions`, `fx_total_exposure_brl`,
  `fx_drift_alerts_total`; latência/falhas do feed de mercado.

## Tests Required

- **Unit/domain:** o exemplo 7.2 (subsídio 150, drift 150, gap 300) com sinais; subsídio negativo
  quando vende acima do mercado; marcação de drift e cruzamento de limite.
- **Integração (Testcontainers + feed fake):** confirmar venda abre posição com subsídio correto;
  atualização de mercado gera `BookPositionDrifted` no limite; liquidação fecha a posição com gap correto.
- **Regressão:** `totalGap` da posição == per-case `fxGainLoss` (sinal) da SPEC-0007 (consistência).

## Acceptance Criteria

- A venda do exemplo gera subsídio 150 na abertura e, na liquidação a 5,70, drift 150 e gap 300.
- `GET /exposure` soma as posições abertas e alerta quando o drift passa do limite.
- `promo-fx?period=` separa subsídio × drift × gap do período.
- `./mvnw verify` verde.

## Open Questions

- **Fonte oficial** da taxa de mercado (qual provedor/feed; fechamento PTAX vs intradiário) — confirmar.
- **Escopo** do congelamento (global vs por agência/produto — 7.3 "a confirmar") afeta como as posições
  agrupam; assumido **global** no v1.
- **Limite de drift** (valor de alerta) — parâmetro governado a definir (SPEC-0014).

## Out of Scope

Aconselhamento prescritivo de câmbio (SPEC-0013), taxa congelada e histórico (SPEC-0003).
