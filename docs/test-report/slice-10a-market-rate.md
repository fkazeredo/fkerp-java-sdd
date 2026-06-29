# Caderno de testes — Slice 10a: Market Rate (SPEC-0011, BR1)

## Escopo

Ingestão da **taxa de mercado** por par como série temporal append-only (`MarketRate`, BR1): registro
manual de contingência (`POST /api/exchange/market-rates`, source MANUAL — DL-0025), "mercado agora"
= observação mais recente `observedAt <= now`, histórico paginado, e a porta `MarketRateProvider`
consumida in-process (pela abertura de `FxPosition`, slice 10b). Cobre a parte de mercado dos
Acceptance Criteria da SPEC-0011 e prepara o cálculo de subsídio.

## Casos de teste

### Integração (Testcontainers) — `MarketRateIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `recordsManualObservationAndServesMarketNow` | POST 5,55 → 201 com source=MANUAL; `current` → 5,55 | BR1 + DL-0025 |
| `marketNowIsTheMostRecentObservationNotInTheFuture` | observação futura (5,70) não é "mercado agora"; porta `MarketRateProvider` devolve 5,50 | BR1 ("≤ now") |
| `returns404WhenNoMarketRateForPair` | `current` p/ par sem observação → 404 `exchange.market-rate.not-found` | Error Behavior |
| `rejectsNonPositiveRateWith400` | rate 0 → 400 `exchange.rate.invalid` | BR1 (taxa > 0) |
| `listsHistoryNewestFirst` | histórico paginado, `observedAt` desc | API |

### Arquitetura
Tudo vive no módulo `exchange` (Rule Zero — sem módulo novo); entidade `MarketRate` e repositório em
`internal` (privados); só serviço, porta `MarketRateProvider`, VOs/views e exceção públicos. Spring
Modulith `verify()` verde. `ExchangeMarketRateNotFoundException` registrada em `HttpErrorMapping`
(teste de completude verde).

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 162` (Fase 4: 157 → +5). Spotless
**283 files clean**, Checkstyle **0 violations**, ArchUnit + Modulith verdes.

## Cobertura — o que NÃO está coberto e por quê

- **Feed externo real** (PTAX/GDS) — fora de escopo do v1 (DL-0025): entra como adapter/ACL futuro
  implementando a mesma porta; aqui prova-se o caminho manual + a porta.
- **Tela Angular** — backend-first (follow-up, como nas fases 2–4).
- A seleção "mercado agora" é testada no nível de integração (query real ao Postgres), provando
  exatamente "maior `observedAt <= now`", inclusive ignorando a observação futura.

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=MarketRateIntegrationTest
```
