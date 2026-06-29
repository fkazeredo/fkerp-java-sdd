# Caderno de testes — Slice 10c: Relatórios de câmbio (SPEC-0011, BR6/BR9)

## Escopo

Os primeiros relatórios de câmbio como **read-models/projeções** (persistence.md — não forçados pelo
agregado): `LiveExposure` (`GET /api/exchange/exposure`) soma as posições OPEN do livro (subsídio +
drift atual, BR6) e dispara o **alerta de drift** quando `|drift| > 2%` da exposição estrangeira
aberta valorada ao mercado do congelamento (BR9/DL-0027), publicando `BookPositionDrifted` (alerta —
não bloqueia); `PromoFxResult` (`GET /api/exchange/reports/promo-fx?period=YYYY-MM`) separa o gap do
período em **subsídio × drift × gap**. Cobre os Acceptance Criteria de exposição e promo-fx da
SPEC-0011.

## Casos de teste

### Integração (Testcontainers) — `ExchangeExposureIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `aggregatesOpenPositionsAndAlertsWhenDriftCrossesTwoPercent` | **2 posições**: subsídio 2×150=**300,00**; base exposição 2×(1000×5,55)=11100 → limite 2%=**222,00**; mercado 5,80 → drift 2×250=**500,00** > 222 → **alerta** + `BookPositionDrifted` | AC2 (soma + alerta) |
| `doesNotAlertWhenDriftIsWithinThreshold` | mercado 5,60 → drift **100,00** < 222 → **sem** alerta | AC2 (limite) |
| `emptyBookHasZeroExposureAndNoAlert` | livro vazio → 0 posições, exposição 0,00, sem alerta | empty-result |
| `promoFxSplitsSubsidyDriftAndGapForThePeriod` | posição fechada do 7.2 no período → subsídio **150,00**, drift **150,00**, gap **300,00** | AC3 (subsídio × drift × gap) |
| `promoFxRejectsMalformedPeriodWith400` | `period=2026-13-XX` → 400 `exchange.period.invalid` | Validation |

> O movimento de mercado é uma **fixture controlada**: registra-se uma observação de mercado mais
> recente (`5.80`/`5.60`) e o `LiveExposure` marca a mercado por essa observação (relógio do teste).

### Arquitetura
`ExchangeExposureService` é projeção sobre as `FxPosition` (repo interno, mesmo módulo); não muta o
agregado. `ExchangePeriodInvalidException` registrada em `HttpErrorMapping`. Modulith `verify()` verde.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 179` (Slice 10b: 174 → +5). Spotless
clean, Checkstyle **0 violations**, ArchUnit + Modulith verdes.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** de exposição/relatórios — backend-first (follow-up).
- **Métricas Prometheus** (`fx_open_positions`, `fx_total_exposure_brl`, `fx_drift_alerts_total`) —
  o projeto ainda registra observabilidade como **business-event logging** (sem Micrometer wired); os
  eventos de negócio (subsídio, cruzamento de limite, fechamento) são logados com bookingId/valores/
  correlation id, conforme o padrão das fases anteriores. Ligar o `MeterRegistry` é melhoria futura.
- Multi-par no agregado: o cálculo é por par via `MarketRateProvider`; testado com USD/BRL (v1 global
  por par — DL-0026).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=ExchangeExposureIntegrationTest
```
