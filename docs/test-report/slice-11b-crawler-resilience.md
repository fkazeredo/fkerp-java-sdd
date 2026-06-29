# Caderno de testes — Slice 11b: Crawler ACL + fila + disjuntor (SPEC-0012, BR1/BR2/BR6/BR7)

## Escopo

O **crawler técnico** em `infra.integration.pointclock` (ACL/DL-0030) e a **primeira resiliência de
saída** do projeto (DL-0031): porta `PointClockSource` (fonte do REP, com mock injetor de falhas),
disjuntor `PointClockCircuitBreaker` (CLOSED/OPEN/HALF_OPEN sobre `Clock`), retry com **dead-letter**,
classificação de falha, tradutor ACL (`PortalMirror` externo → `CollectSnapshotCommand` de domínio),
agendador `PointClockCrawlScheduler` (`infra/jobs`) e disparo manual `POST /api/integration/point/crawl`.
Cobre os Acceptance Criteria "Falhas de portal abrem o circuit breaker e alertam, sem produzir snapshot
falso" e "ArchUnit confirma que o crawler não escreve em outro módulo".

## Casos de teste

### Unitário — `PointClockCircuitBreakerTest` (relógio controlado, sem sleep)
| Caso | Verifica | Regra |
|---|---|---|
| `opensAfterThresholdConsecutiveFailuresAndShortCircuits` | N falhas consecutivas → OPEN; `allowRequest` falso dentro do cooldown | BR2 / `messaging-and-integrations.md` |
| `halfOpensAfterCooldownAndClosesOnSuccess` | avançar o relógio além do cooldown → HALF_OPEN; sucesso → CLOSED | BR2 |
| `reOpensWhenTheHalfOpenTrialFails` | falha no trial HALF_OPEN → volta a OPEN | BR2 |

### Integração (Testcontainers + fonte fake) — `PointClockCrawlerIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `successfulCrawlPublishesAnOperationalSnapshotAndRecordsTheRun` | coleta OK → snapshot `operationalOnly=true` + run SUCCEEDED | BR2/BR7 |
| `retryableFailureThatRecoversWithinAttemptsSucceeds` | TIMEOUT depois sucesso → snapshot; 2 hits; run RETRY_SCHEDULED + SUCCEEDED | BR2 (retry) |
| `persistentRetryableFailureExhaustsAttemptsAndDeadLettersWithoutSnapshot` | 3× UNAVAILABLE → **DEAD_LETTER** + `PointCrawlingFailed`, **sem snapshot** | BR2 (dead-letter) — **sem snapshot falso** |
| `authenticationFailureIsFatalAndNotRetried` | AUTHENTICATION_FAILED → **1 hit só**, dead-letter, sem snapshot | BR2 (classe fatal não retenta) |
| `breakerOpensAfterRepeatedFailuresAndShortCircuitsTheNextCallWithoutHittingThePortal` | breaker abre; **próxima chamada não bate na fonte** (`source.calls()` inalterado) | BR2 (disjuntor real) |

### Arquitetura — `ArchitectureTest` (2 regras novas, com teeth)
| Regra | Verifica |
|---|---|
| `DOMAIN_MUST_NOT_DEPEND_ON_POINT_CLOCK_ADAPTER` | o DTO externo `PortalMirror`/o adaptador **não cruza** para o domínio (ACL, BR6) |
| `POINT_CLOCK_CRAWLER_MUST_NOT_WRITE_INTO_THE_CORE` | o crawler **não depende** de quoting/booking/exchange/reconciliation/commissioning/finance/accounts/sourcing (BR6) — prova que nunca escreve no miolo |

> As regras de fronteira do projeto têm teeth comprovado em `ArchitectureRulesHaveTeethTest` (plantar
> uma violação `domain→infra` reprova). Plantar `pointclock → domain.booking` reprovaria a 2ª regra.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 197` (slice 11a: 187 → +10). Spotless
clean, **0 Checkstyle violations**, ArchUnit (9 regras) + Spring Modulith (11 módulos) verdes.

## Cobertura — o que NÃO está coberto e por quê

- **Cliente HTTP real do portal** — fora de escopo (DL-0029/0031): a porta `PointClockSource` tem um
  binding de simulação rastreável em produção; os testes injetam falhas para provar a resiliência. O
  cliente real entra como adapter futuro implementando a mesma porta.
- **Métricas Prometheus** (`point_crawl_failures_total{class}`, estado do breaker) — por ora **log de
  evento de negócio** + estado do breaker logado nas transições (padrão das fases 1–5; follow-up).
- **Tela Angular** — backend-first.

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=PointClockCircuitBreakerTest,PointClockCrawlerIntegrationTest,ArchitectureTest
```
