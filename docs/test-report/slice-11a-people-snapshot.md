# Caderno de testes — Slice 11a: People + snapshot operacional (SPEC-0012, BR2/BR3/BR5/BR7)

## Escopo

Lado **operacional** do ponto: novo módulo `people` (11º Modulith, DL-0030) dono do agregado
`PointSnapshot` (`operationalOnly=true` por invariante, BR3), da **idempotência** por `(sourceRef,
periodRef)` (BR5) e do **histórico de execução** `PointCrawlRun` (BR7). Caso de uso público
`PointSnapshotService.collect` (a costura que o crawler dirige) + endpoints de leitura
`GET /api/integration/point/snapshots/{id}` e `GET /api/integration/point/runs`. Cobre os Acceptance
Criteria "job publica snapshot operacional para o People, com histórico" e a **regressão** "snapshot
raspado nunca é documento legal".

## Casos de teste

### Unitário — `PointSnapshotTest` (regressão BR3/BR5)
| Caso | Verifica | Regra |
|---|---|---|
| `aCollectedSnapshotIsAlwaysOperationalOnly` | snapshot coletado → `operationalOnly=true` (invariante do agregado) — **impede** tratar o raspado como documento legal | **BR3 (regressão)** |
| `refreshUpdatesContentInPlaceKeepingIdentityAndOperationalFlag` | re-coleta atualiza marks/instante **sem** mudar a identidade; flag continua true | BR5 |

### Unitário — `PointFailureClassTest`
| Caso | Verifica | Regra |
|---|---|---|
| `transientNetworkFailuresAreRetryable` | TIMEOUT/UNAVAILABLE são retetáveis | BR2 |
| `fatalFailuresAreNotRetryable` | AUTHENTICATION_FAILED/INVALID_RESPONSE/UNKNOWN_ERROR **não** retentam | BR2 (`messaging-and-integrations.md`) |

### Integração (Testcontainers) — `PointSnapshotIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `collectsAnOperationalSnapshotReadableViaTheApi` | `collect` persiste e `GET …/snapshots/{id}` → 200 com `operationalOnly=true` | BR2/BR3 |
| `reCollectingTheSamePeriodIsIdempotent` | 2ª coleta do mesmo `(sourceRef,periodRef)` retorna o **mesmo id**; `count(point_snapshots)=1` | **BR5** |
| `rejectsAnInvalidCollectAndReturnsTheStableError` | id inexistente → 404 `point.snapshot.not-found` | Error Behavior |
| `crawlRunHistoryRecordsSuccessAndFailure` | run SUCCEEDED e run DEAD_LETTER (com `failure_class`) gravados; `…/runs?status=DEAD_LETTER` lista | **BR7** |

### Arquitetura
Módulo `people` com base pública (serviço, comandos, views, eventos, exceções) e `internal`
(entidades/repos) privado. Spring Modulith `verify()` verde com **11 módulos** (people é leaf — sem
ciclo). `PointSnapshotNotFoundException`/`PointSnapshotInvalidException` registradas em
`HttpErrorMapping` (teste de completude verde). i18n pt-BR + fallback EN.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 187` (Fase 5: 179 → +8). Spotless clean,
Checkstyle **0 violations**, ArchUnit + Modulith (11 módulos) verdes. Migração `V16` aplicada (Postgres
real).

## Cobertura — o que NÃO está coberto e por quê

- O **crawler técnico** (cliente do portal, fila, disjuntor, parser do espelho) e o evento
  `PointSnapshotCollected` ponta a ponta entram na **slice 11b** (esta fatia entrega o caso de uso que
  o crawler dirige e prova a idempotência/histórico).
- **Tela Angular** — backend-first (follow-up, padrão das fases 2–5).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=PointSnapshotIntegrationTest,PointSnapshotTest,PointFailureClassTest
```
