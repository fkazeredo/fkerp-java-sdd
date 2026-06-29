# DL-0031 — Disjuntor + fila de retry + dead-letter in-process (sem resilience4j); origem do REP atrás de porta com mock injetor de falhas

- **Fase:** 6 (Crawler de ponto)
- **Spec(s):** SPEC-0012 (BR2 retry/circuit breaker por classe de falha; Validation "timeout/retry/circuit
  breaker por classe"; Acceptance "Falhas de portal abrem o circuit breaker e alertam, sem produzir snapshot
  falso"); `messaging-and-integrations.md` (§External integrations and resilience, §Background jobs, §Idempotency)
- **ADR relacionado:** 0010 (porta por adaptador técnico), 0001 (monólito modular)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0012 exige **fila + circuit breaker + retry/dead-letter** como a **primeira resiliência de saída** do
projeto, mas não diz **com qual mecanismo**. O `pom.xml` não traz `resilience4j` nem broker (Kafka/Rabbit). É
preciso decidir o mecanismo, mantendo a Regra Zero (sem infra especulativa) e a testabilidade determinística
(relógio controlado, mock injetor de falhas).

## Decisão

- **Implementar disjuntor, fila de retry e dead-letter como código in-process determinístico**, no adaptador
  `com.fksoft.infra.integration.pointclock`, **sem** adicionar resilience4j nem message broker:
  - **`PointClockCircuitBreaker`**: estados `CLOSED → OPEN → HALF_OPEN`. Abre após **N falhas consecutivas**
    (default 3); enquanto `OPEN`, recusa a chamada de saída por uma janela de cooldown (default 60s, medida no
    `Clock` injetado); após o cooldown vai a `HALF_OPEN` e uma tentativa de sucesso o fecha. **Controlado por
    `Clock`** → testável sem `sleep`.
  - **Fila/retry com dead-letter**: cada execução do crawl é um **item de trabalho** com `attempts` e estado
    `PENDING → RUNNING → (DONE | RETRY_SCHEDULED | DEAD_LETTER)`. Após `maxAttempts` (default 3) o item vai a
    **`DEAD_LETTER`** (estado de falha persistido no `point_crawl_runs.status`), e publica `PointCrawlingFailed`
    — **nunca** um snapshot falso (fallback enganoso é proibido por `messaging-and-integrations.md`).
  - **Classes de falha** (`PointFailureClass`): `TIMEOUT, UNAVAILABLE, AUTHENTICATION_FAILED, INVALID_RESPONSE,
    UNKNOWN_ERROR` — a classe decide se é retetável (TIMEOUT/UNAVAILABLE) ou fatal (AUTHENTICATION_FAILED →
    não retenta, vai direto a DEAD_LETTER) e alimenta a observabilidade.
- **A origem do REP fica atrás de uma porta** `PointClockSource` (no adaptador, contrato de saída) com **timeout**
  por chamada. O **mock** `FaultInjectingPointClockSource` (teste, `simulation-and-mocking.md`) injeta falhas
  por classe para provar breaker/retry/dead-letter de forma determinística.

## Justificativa

- **Regra Zero / `core-principles.md`:** resilience4j e brokers são peso real; o caso (uma fonte HTTP, um job
  agendado, in-process, single-instance — ADR 0002) é resolvido por algumas dezenas de linhas testáveis. Patterns
  existem para resolver problema concreto, não por moda. Um disjuntor próprio, pequeno e coberto por teste, é a
  solução mais simples que satisfaz a spec.
- **`messaging-and-integrations.md`:** todo call externo tem timeout; retries são intencionais (só classes
  retetáveis); breaker quando a dependência pode degradar; fallback **não** pode produzir resultado de negócio
  enganoso — exatamente o desenho acima.
- **Determinismo:** breaker e cooldown sobre `Clock` injetado + mock injetor de falhas = teste sem flakiness e
  sem `Thread.sleep`, como o resto do projeto (relógio controlado).
- **Single-instance (ADR 0002):** sem concorrência multi-nó, não é preciso lock distribuído nem fila externa;
  estado em memória + histórico persistido em `point_crawl_runs` basta.

## Alternativas descartadas

- **Adicionar resilience4j.** Descartada agora (Regra Zero): traz dependência e configuração para um único ponto
  de saída single-instance; o disjuntor próprio é menor e mais testável. Reavaliar quando houver **vários**
  integradores de saída (aí um ADR liga resilience4j de uma vez).
- **Broker real (Kafka/Rabbit) para a fila.** Descartada: nenhum requisito de durabilidade entre processos
  justifica o custo operacional; o histórico em Postgres (`point_crawl_runs`) cobre auditoria/retomada.
- **Retry cego sem classificação.** Descartada: `messaging-and-integrations.md` proíbe — `AUTHENTICATION_FAILED`
  não pode ser retentado em loop; a classe decide.

## Impacto

- **Arquivos:** `PointClockCrawler` (orquestra), `PointClockCircuitBreaker`, `PointFailureClass`,
  `PointClockSource` (porta de saída) + `FaultInjectingPointClockSource` (mock de teste), tudo em
  `infra.integration.pointclock`. Histórico em `point_crawl_runs` (estado `DEAD_LETTER` inclusive).
- **Eventos:** `PointCrawlingFailed {sourceRef, failureClass, occurredAt}` (consumido por observabilidade).
- **Observabilidade:** logs por execução (sourceRef, classe, latência, itens, correlation id) **sem credenciais**;
  estado do breaker logado nas transições.
- **Sem mudança no `pom.xml`** (nenhuma dependência nova).

## Como reverter

Reversão **moderada**: trocar o disjuntor próprio por resilience4j é encapsulado no `PointClockCrawler` (a porta
`PointClockSource` e os eventos não mudam); trocar a fila in-process por broker exige outbox/consumer, mas o
contrato de eventos e o histórico persistido permanecem. Os testes de breaker/retry/dead-letter continuam válidos
como contrato de comportamento.
