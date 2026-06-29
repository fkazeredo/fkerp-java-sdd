# 0003 - Exchange · Taxa de venda congelada (Open-Host Service)

Status: Approved
Related ADRs: 0012, 0014

## Goal

Permitir que o diretor **fixe uma taxa única de venda** por par de moeda (o "câmbio congelado") e
**servir a taxa vigente** a quem compõe uma cotação, como **Open-Host Service**. Histórico
append-only e auditável.

## Scope

**Em escopo:** registrar uma `PinnedSellRate` por par de moeda; consultar a taxa vigente (Open-Host)
e o histórico; expor uma fachada de domínio (`ExchangeRateProvider`) consumida in-process por
`Quoting` (SPEC-0005). Tela mínima: formulário "fixar taxa" + tabela de histórico por par.

**Fora de escopo (vai para a Fase 5 / SPEC-0011):** taxa de mercado, exposição/posição agregada do
livro (`ExchangeExposure`), decomposição subsídio (intencional) × drift (risco), promoções.

## Business Context

A Acme Travel vende em reais hoje e paga o fornecedor em dólar depois → há **exposição** no tempo. O
diretor congela uma **taxa única de venda** (ex. 5,40) e a usa como alavanca comercial/promoção. O
fornecedor é pago no câmbio real — mas medir esse gap é assunto da Fase 5. Aqui modelamos apenas a
**taxa servida** e o fato de fixá-la. `Exchange` é dono da taxa (redesenho 7.2).

## Business Rules

```txt
BR1  PinnedSellRate tem { currencyPair (base/quote, ex. USD/BRL), rate (BigDecimal scale 6, > 0),
     effectiveFrom (Instant), setBy, note? }.
BR2  Append-only: fixar uma nova taxa NUNCA muta uma anterior; a nova prevalece a partir do seu
     effectiveFrom.
BR3  A "taxa vigente" de um par no instante t = a de maior effectiveFrom <= t. Se não houver
     nenhuma <= t => exchange.rate.not-found.
BR4  rate MUST ser > 0; senão 400 exchange.rate.invalid.
BR5  effectiveFrom pode ser passado, agora ou futuro (suporta agendar uma taxa). A vigência sempre
     considera "<= agora".
```

> A taxa congelada é **única e global por par** no v1 (redesenho). Escopo por agência/produto da
> taxa (redesenho 7.3, "a confirmar") fica adiado.

## Input/Output Examples

```http
POST /api/exchange/pinned-rates
{ "currencyPair": "USD/BRL", "rate": 5.400000, "note": "promo Orlando" }
201 Created
{ "id": "a12...", "currencyPair": "USD/BRL", "rate": 5.400000,
  "effectiveFrom": "2026-06-26T12:00:00Z", "setBy": "diretor", "note": "promo Orlando" }
```

```http
GET /api/exchange/pinned-rates/current?pair=USD-BRL
200 OK
{ "currencyPair": "USD/BRL", "rate": 5.400000, "effectiveFrom": "2026-06-26T12:00:00Z", "setBy": "diretor" }

GET /api/exchange/pinned-rates/current?pair=EUR-BRL   (nenhuma taxa fixada)
404 Not Found
{ "code": "exchange.rate.not-found", "message": "Nenhuma taxa vigente para o par informado.", "fields": [] }
```

## API Contracts

- `POST /api/exchange/pinned-rates` — body `{currencyPair, rate, effectiveFrom?, note?}` → 201.
- `GET /api/exchange/pinned-rates/current?pair=USD-BRL` → 200 | 404 `exchange.rate.not-found`.
- `GET /api/exchange/pinned-rates?pair=USD-BRL&page=&size=` → 200 `PageResponse` (histórico, sort
  por `effectiveFrom desc`).
- **Fachada (porta) consumida por Quoting:** `ExchangeRateProvider.currentRate(CurrencyPair)` →
  `Optional<PinnedSellRate>`. Não é REST entre módulos; é chamada in-process via fachada pública
  (Spring Modulith). OpenAPI atualizada para os endpoints REST.

## Events

- `RatePinned` — fato: uma taxa de venda foi fixada. Payload `{currencyPair, rate, effectiveFrom,
  setBy, occurredAt}`. Produtor: `exchange`. Consumidores: nenhum ainda (futuro: Intelligence,
  Compliance/auditoria). Interno in-process por ora.

## Persistence Changes

```txt
V3__create_pinned_sell_rates.sql
  pinned_sell_rates(
    id uuid PK,
    currency_pair varchar not null,
    rate numeric(18,6) not null,        -- scale 6
    effective_from timestamptz not null,
    set_by varchar not null,
    note varchar null,
    created_at timestamptz not null
  )
  INDEX ix_rates_pair_effective (currency_pair, effective_from DESC)
```

Tabela **append-only** (sem UPDATE/DELETE em produção). `CHECK (rate > 0)` no banco reforça BR4.

## Validation Rules

- Delivery: formato do par, `rate` numérico.
- Domain: `rate > 0` (BR4) e formato do `CurrencyPair` (value object) como invariantes.
- Persistence: `CHECK (rate > 0)`; nenhuma mutação de linhas existentes.

## Error Behavior

- `exchange.rate.invalid` → 400; `exchange.rate.not-found` → 404. Chaves i18n pt-BR.

## Observability Requirements

- Logar `RatePinned` como evento de negócio (par, taxa, quem, quando). Correlation id.
- Métrica opcional `exchange_rate_pinned_total{pair}`.

## Tests Required

- **Unit:** seleção da taxa vigente com várias `effectiveFrom`, **incluindo uma futura** (a vigente
  deve ser a maior `<= agora`, não a futura).
- **Unit:** `CurrencyPair`/`rate` rejeitam entradas inválidas.
- **Integração (Testcontainers):** POST 201; current 200; histórico paginado; current sem taxa →
  404; rate ≤ 0 → 400.
- **Regressão:** trocar a taxa por uma mais nova não deve afetar a seleção para instantes anteriores;
  taxa futura não é servida antes do tempo.

## Acceptance Criteria

- Fixar USD/BRL 5,40 → `current` retorna 5,40.
- Fixar depois USD/BRL 5,55 com `effectiveFrom` no futuro → `current` continua 5,40 até aquele
  instante.
- `current` para EUR/BRL sem taxa → 404 `exchange.rate.not-found`.
- `ExchangeRateProvider.currentRate(...)` devolve a taxa correta para Quoting.
- `mvnw verify` verde.

## Open Questions

- ~~Nome `Exchange` (Q1 da Parte 13) — assumido sim; confirmar.~~ → **ASSUMIDO** (2026-06-29):
  mantido `Exchange`. Ver [DL-0008](../decision-log/DL-0008-exchange-nome-do-modulo.md).
- Escopo da taxa por agência/produto (redesenho 7.3) — adiado (no v1 é global por par).
- Política de arredondamento da **conversão** (HALF_UP, scale 2) é definida em Quoting (SPEC-0005).

## Out of Scope

Taxa de mercado/feed externo, exposição/posição do livro, subsídio × drift, promoções, conversões
multi-perna. (Fase 5.)
