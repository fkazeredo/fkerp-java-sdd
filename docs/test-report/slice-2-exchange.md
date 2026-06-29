# Caderno de testes — Slice 2: Exchange (SPEC-0003)

## Escopo

Taxa de venda congelada (Open-Host): fixar `PinnedSellRate` por par (append-only, BR2), servir a
taxa vigente (maior `effectiveFrom <= agora`, BR3), histórico paginado, e a fachada
`ExchangeRateProvider` consumida in-process por Quoting. Cobre os Acceptance Criteria da SPEC-0003
(exceto a tela Angular — ver Cobertura). Nome do módulo `Exchange` confirmado
([DL-0008](../decision-log/DL-0008-exchange-nome-do-modulo.md)).

## Casos de teste

### Unitário — `CurrencyPair` (`CurrencyPairTest`)
| Caso | Verifica |
|---|---|
| `parsesSlashAndDashSeparatorsToCanonicalText` | aceita `USD/BRL` e `usd-brl`, normaliza para `USD/BRL` |
| `rejectsMalformedPairs` (7 valores) | formato inválido → `exchange.pair.invalid` |
| `rejectsNull` | nulo rejeitado |

### Integração (Testcontainers) — `ExchangeIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `pinsAndServesCurrentRate` | POST 5,40 → 201; `current` → 5,40 | AC1 |
| `doesNotServeAFutureDatedRateBeforeItsTime` | taxa futura (5,55) não vira vigente; `current` segue 5,40; idem via `ExchangeRateProvider` | AC2 + AC4 + regressão |
| `returns404WhenNoRateForPair` | `current` p/ par sem taxa → 404 `exchange.rate.not-found` | AC3 |
| `rejectsNonPositiveRateWith400` | rate 0 → 400 `exchange.rate.invalid` | BR4 |
| `listsHistoryNewestFirst` | histórico paginado, `effectiveFrom` desc | API |

### Arquitetura
Módulo `exchange` (`@ApplicationModule`) verificado por Spring Modulith; `internal` (entity/repo)
privado; só fachada/serviço/port/VOs públicos.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 46` (Slice 1: 32 → +14). Spotless
**63 files clean**, Checkstyle **0 violations**.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** ("fixar taxa" + tabela de histórico) — **pendente** (leva de frontend da Fase 1).
- Seleção da taxa vigente é testada **no nível de integração** (consulta real ao Postgres), não como
  função pura: a regra "maior `effectiveFrom <= agora`" é uma query `order by ... limit 1`, e o teste
  de integração prova exatamente esse comportamento, inclusive ignorando a taxa futura.
- Escopo por agência/produto da taxa e arredondamento da conversão — fora de escopo (SPEC-0011 / Quoting).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=CurrencyPairTest
./mvnw test -Dtest=ExchangeIntegrationTest
```
