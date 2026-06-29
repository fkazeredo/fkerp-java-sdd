# Caderno de testes — Slice 10b: FxPosition (SPEC-0011, BR2-BR5)

## Escopo

A posição cambial (`FxPosition`) e a decomposição do gap em **subsídio × drift** (OVERVIEW 7.2).
Aberta quando uma venda confirmada carrega custo em moeda estrangeira à taxa congelada (BR2),
acumulando o **subsídio** na abertura (BR3); o **drift** é marcado a mercado enquanto aberta (BR4);
fecha na liquidação com **realizedDrift** e **totalGap** (BR5). Dirigida por Reconciliation (que
segura a proveniência congelada), direção `reconciliation → exchange` (acíclica — DL-0028); o
`exchange` é dono da matemática. Cobre os Acceptance Criteria de abertura/fechamento da SPEC-0011 e a
regressão de consistência com o `fxGainLoss` por-caso da SPEC-0007.

## Casos de teste

### Unitário/domínio — `FxPositionTest` (a prova exigida pela fase)
| Caso | Verifica | Regra |
|---|---|---|
| `accruesSubsidyOnOpening` | subsídio = (5,55 − 5,40) × 1000 = **150,00**; OPEN; drift/gap nulos | BR3 + 7.2 |
| `marksDriftToMarketWhileOpen` | drift a 5,70 = **150,00**; a 5,55 = 0,00; a 5,45 = −100,00 | BR4 (sinais) |
| `closesWithRealizedDriftAndTotalGapMatchingTheCanonicalExample` | realizedDrift **150,00**, totalGap **300,00**; identidade `totalGap == (settle−pinned)×amount`; view fechada sem mark-to-market | BR5 + 7.2 |
| `negativeSubsidyWhenSoldAboveMarket` | pinned 5,60 > mercado 5,55 → subsídio **−50,00** | BR3 (sinal) |
| `roundsSubsidyHalfUpAtScaleTwo` | (5,555001−5,55)×333,33 → **1,67** HALF_UP | money scale 2 HALF_UP |
| `closeIsIdempotent` | segundo `close` ignorado; números do 1º permanecem | robustez |
| `exposureValueAtFreezeIsForeignAmountTimesMarketAtFreeze` | 1000 × 5,55 = **5550,00** (base do alerta de livro) | BR9 |

### Integração (Testcontainers) — `FxPositionIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `opensPositionAccruingSubsidyOnConfirmation` | confirmar venda (USD 1000, pinned 5,40, mercado 5,55) → posição OPEN, subsídio **150,00**, proveniência | AC1 (abertura) |
| `closesPositionWithRealizedDriftAndTotalGapOnSettlement` | liquidação a 5,70 → CLOSED, realizedDrift **150,00**, totalGap **300,00** | AC1 (liquidação) |
| `totalGapMatchesReconciliationFxGainLossSign` | `fxGainLoss` (Recon) = −300; `totalGap` (Exchange) = +300 → `totalGap == −fxGainLoss` | **Regressão** (consistência SPEC-0007) |
| `doesNotOpenAPositionWithoutAMarketRate` | sem MarketRate, confirmação ok mas **não** abre posição → 404 | BR2 (guarda) |
| `returns404ForUnknownBooking` | booking sem posição → 404 `exchange.position.not-found` | Error Behavior |

### Arquitetura
`exchange` **não** depende de `quoting`/`booking` (evita ciclo — o Spring Modulith reprovou a 1ª
tentativa de ler `QuoteDirectory` dentro do `exchange`; corrigido invertendo a direção para
`reconciliation → exchange`). `FxPosition` e repositório em `internal`. `ExchangePositionNotFoundException`
registrada em `HttpErrorMapping`. Modulith `verify()` verde.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 174` (Slice 10a: 162 → +12). Spotless
clean, Checkstyle **0 violations**, ArchUnit + Modulith verdes.

## Cobertura — o que NÃO está coberto e por quê

- **LiveExposure agregado + alerta de drift** e **PromoFxResult(período)** — slice 10c (read-models).
- **Tela Angular** — backend-first (follow-up).
- O drift mark-to-market é exposto no view por um `marketNow` lido do `MarketRateProvider`; o cálculo
  puro é provado em unidade (`driftAt`), e a abertura/fechamento ponta a ponta na integração.

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=FxPositionTest
./mvnw test -Dtest=FxPositionIntegrationTest
```
