# 0027 - Observabilidade & monitoramento (Micrometer/Actuator/Prometheus + logs JSON + stack infra/ + /api/version)

Status: Approved
Fase: 11
Related ADRs: 0012 (camadas hexagonais — observabilidade vive em infra, não no domain), 0015 (SemVer),
0010 (porta por módulo), 0014 (módulos)
Related DLs: DL-0095 (exposição/segurança do Actuator), DL-0096 (logs JSON nativos Spring Boot +
mascaramento), DL-0097 (`/api/version` por build-info + git), DL-0098 (métricas de negócio sobre os
eventos já existentes, instrumentadas em infra)

## Goal

Trazer a stack de observabilidade do projeto irmão **fkerp-poc** para o ERP, **sem inventar nenhum
comportamento de negócio novo** (Regra Zero): instrumentar e **expor** o que já existe.

1. **Métricas** — Micrometer + Spring Boot Actuator + registry Prometheus em `/actuator/prometheus`
   (técnicas: HTTP, JVM, pool de conexões; e **de negócio**, contadores/temporizadores sobre os
   eventos de domínio que as fases anteriores já publicam).
2. **Logs estruturados em JSON** — com **correlation id** (já existente, via MDC) e **dado
   pessoal/segredo mascarado**, no formato coletável pelo Loki via Grafana Alloy.
3. **Stack de monitoramento** sob `infra/` — **Prometheus + Loki + Grafana Alloy + Grafana** via Docker
   Compose, com datasources e um dashboard pré-provisionados (espelha o `infra/` da POC).
4. **`GET /api/version`** — versão (SemVer), commit git e hora do build, a partir do **build-info** do
   Spring Boot (público; só metadados, sem segredo).

## Scope

**Em escopo (backend + infra, sem mudar regra de negócio):**

- **Actuator + Micrometer + registry Prometheus**: dependência `micrometer-registry-prometheus`;
  expor deliberadamente `health`, `info`, `prometheus` (e `metrics`); proteger o resto.
- **Segurança dos endpoints** (DL-0095): `/actuator/health`, `/actuator/health/**`, `/actuator/info`
  e `/api/version` ficam **públicos** (probes/UI); os endpoints **sensíveis** de operação
  (`/actuator/prometheus`, `/actuator/metrics`, e qualquer outro exposto) exigem **`ROLE_IT`**
  (papel de TI já existente — SPEC-0024). `env`, `beans`, `heapdump`, `threaddump`, `loggers`,
  `configprops`, etc. **não** são expostos.
- **`GET /api/version`** (DL-0097): payload `{ version, gitCommit, buildTime }`, a partir de
  `BuildProperties` + `GitProperties` (build-info do Spring Boot + `git-commit-id-maven-plugin`),
  com **degradação graciosa** quando rodando fora de um build empacotado (sem `git.properties`).
- **Logs JSON** (DL-0096): logging estruturado **nativo do Spring Boot 3.4+**
  (`logging.structured.format.console=ecs`/`logstash`) ligado por padrão no container; o
  **correlation id** entra no JSON (MDC); **nenhum segredo/PII** é logado (reforço da higiene da
  `security.md`, já praticada nos módulos).
- **Métricas de negócio** (DL-0098): contadores/temporizadores derivados dos **eventos de domínio já
  publicados** (ex.: reservas confirmadas/canceladas, cotações compostas, overrides de preço, NF de
  comissão emitida, período fechado, login). Instrumentadas por um **listener em infra** que consome
  os eventos exportados (o domain **não** depende de Micrometer — ADR 0012).
- **`infra/`** (espelha a POC): `prometheus/prometheus.yml`, `loki/loki-config.yml`,
  `alloy/config.alloy`, `grafana/provisioning/{datasources,dashboards}`, `grafana/dashboards/*.json`.
- **Docker Compose**: serviços `prometheus`, `loki`, `alloy`, `grafana` adicionados ao
  `docker-compose.yml` existente, na mesma rede do `app`/`db`; o `app` emite logs JSON.
- **OpenAPI**: documentar `/api/version`.
- **Manual bilíngue** (pt-BR + en-US): como o operador vê métricas/monitoramento e a versão.

**Fora de escopo (Regra Zero — não antecipar):**

- **Tracing distribuído** (OpenTelemetry/Tempo/Zipkin): a POC removeu o datasource Tempo; um monólito
  modular não tem saltos de rede a correlacionar. Fica como gancho futuro (não há dívida — o
  correlation id já amarra os logs). (ver DL-0096)
- **Alertmanager / regras de alerta** acionáveis: a `observability.md` pede "alertas acionáveis", mas
  definir limiares reais é decisão de operação (SRE/dono). A stack sobe pronta para receber regras;
  nenhuma regra é inventada nesta fase.
- **Novos eventos/telemetria de negócio**: só se instrumenta o que **já é publicado**. Nenhum
  `log.info`/evento novo de negócio é criado para "ter métrica".
- **Frontend de observabilidade** (telas de métricas no Angular): o Grafana é a UI de operação; o
  frontend só passa a **exibir** a versão de `/api/version` quando a fatia de UX correspondente pedir.
- **Métricas de IA** (provider/modelo/confiança): não há provedor de LLM em produção ainda.

## Business Context

O ERP é um monólito modular que, até a Fase 10, expõe apenas `/actuator/health` e `/actuator/info` e
loga em texto plano com correlation id. O operador de TI (papel `ROLE_IT`, SPEC-0024) precisa
**enxergar a saúde e o comportamento** do sistema em produção: latência/erros das APIs, memória/threads
da JVM, e **fatos de negócio** em volume (quantas reservas confirmadas, quantos overrides de preço,
quantas NF emitidas) — sem abrir o banco. A POC já resolveu isso com uma stack
**Prometheus + Loki + Alloy + Grafana** e um `/api/version` para o rodapé/about da UI. Esta fase
**traz essa stack** e a **adapta ao modelo de papéis e às camadas** deste projeto, instrumentando os
eventos de domínio que **já existem** — nada de comportamento novo.

A autoridade de autorização **continua sendo o backend** (Spring Security): os endpoints de operação
(metrics/prometheus) ficam atrás de `ROLE_IT`; `health`/`info`/`version` são públicos por serem
probes/metadados sem segredo.

## Business Rules

```txt
BR1  Os endpoints expostos do Actuator são SOMENTE: health, info, prometheus, metrics. Endpoints que
     vazam internals (env, beans, heapdump, threaddump, loggers, configprops, …) NÃO são expostos.
     (ASSUMIDO ver DL-0095)
BR2  /actuator/health, /actuator/health/** e /actuator/info são PÚBLICOS (liveness/readiness + build
     info), assim como GET /api/version (só metadados de build — sem segredo). (ASSUMIDO ver DL-0095/0097)
BR3  /actuator/prometheus e /actuator/metrics (e qualquer outro endpoint sensível de operação) exigem
     o papel ROLE_IT — o backend é a autoridade (security.md). Sem token → 401; token sem ROLE_IT →
     403 (contrato de erro estável). (ASSUMIDO ver DL-0095)
BR4  GET /api/version responde { version, gitCommit, buildTime }: version = SemVer do pom.xml (fonte de
     verdade, ADR 0015); gitCommit = hash curto do commit; buildTime = instante do build (ISO-8601).
     Quando build-info/git.properties não existem (execução fora do jar empacotado), os campos
     ausentes degradam para um marcador estável ("unknown"/"dev"), NUNCA quebram o endpoint.
     (ASSUMIDO ver DL-0097)
BR5  Os logs em produção (container) são JSON estruturado (Spring Boot nativo), incluindo o
     correlationId do MDC. NENHUM segredo (senha, token, chave, secret) e NENHUM dado pessoal
     desnecessário aparece no log — reforça a higiene já praticada (security.md). (ASSUMIDO ver DL-0096)
BR6  As métricas de NEGÓCIO são derivadas EXCLUSIVAMENTE de eventos de domínio JÁ PUBLICADOS, por um
     componente de INFRA que os consome; o domain NÃO importa Micrometer/Actuator (ADR 0012). Nenhum
     evento/log de negócio novo é criado para gerar métrica. (ASSUMIDO ver DL-0098)
BR7  A instrumentação NÃO altera comportamento: contadores/temporizadores são efeitos colaterais
     passivos; uma falha de métrica nunca pode falhar uma operação de negócio.
BR8  A stack de monitoramento (Prometheus/Loki/Alloy/Grafana) vive em infra/ + docker-compose; NÃO faz
     parte de ./mvnw verify (é configuração + operação), e o build do backend permanece verde sem ela.
```

> Todas as BR acima nasceram de Open Questions de operação resolvidas pela ordem do `RUN-PHASE.md`
> (Recomendações do ROADMAP → fontes oficiais Micrometer/Prometheus/Grafana/Spring Boot → valor mais
> defensável). Cada uma referencia o DL que a fixou.

## Acceptance Criteria

```txt
AC1  GET /api/version retorna 200 com { version, gitCommit, buildTime }; version == versão do pom.xml
     (0.22.0); é PÚBLICO (sem token). [BR4, BR2]
AC2  GET /actuator/health retorna 200 e é PÚBLICO; expõe os grupos liveness/readiness. [BR2]
AC3  GET /actuator/prometheus, SEM token, retorna 401 (não vaza métrica a anônimo). [BR3]
AC4  GET /actuator/prometheus, com token de um papel SEM ROLE_IT (ex.: viewer), retorna 403. [BR3]
AC5  GET /actuator/prometheus, com token de ROLE_IT, retorna 200 em formato de exposição Prometheus
     (text/plain; contém séries jvm_* e http_server_requests_*). [BR1, BR3]
AC6  GET /actuator/env e GET /actuator/beans (anônimo ou ROLE_IT) NÃO existem como endpoint exposto
     (404 — não estão no include). [BR1]
AC7  As métricas de negócio aparecem em /actuator/prometheus após a operação correspondente: confirmar
     uma reserva incrementa o contador de reservas confirmadas; compor uma cotação incrementa o de
     cotações; etc. — derivadas de eventos já publicados. [BR6, BR7]
AC8  O registry Micrometer carrega a tag comum application=acme-travel-erp. [BR1]
AC9  docker compose up sobe app + db + prometheus + loki + alloy + grafana; o Prometheus tem o alvo
     backend:8080/actuator/prometheus; o Grafana tem os datasources Prometheus e Loki e o dashboard
     pré-provisionados. (verificação manual/operacional — fora do ./mvnw verify) [BR8]
AC10 ./mvnw verify permanece VERDE com todos os portões (ArchUnit/Modulith/Spotless/Checkstyle); o
     domain NÃO depende de Micrometer/Actuator (ArchUnit). [BR6, BR8]
```

## Tests Required

- **Integração (Testcontainers + segurança real):**
  - `VersionEndpointIntegrationTest` — AC1 (público, payload e versão).
  - `ActuatorExposureIntegrationTest` — AC2 (health público), AC3 (prometheus 401 anônimo), AC4
    (prometheus 403 viewer), AC5 (prometheus 200 IT + formato/séries), AC6 (env/beans não expostos).
  - `BusinessMetricsIntegrationTest` — AC7/AC8 (uma operação de negócio incrementa o contador exposto
    em `/actuator/prometheus`; tag `application` presente).
- **Arquitetura (ArchUnit):** AC10 — uma regra prova que `..domain..` não depende de
  `io.micrometer..`/`org.springframework.boot.actuate..` (e o teste "teeth" continua provando que a
  regra de camadas falha ao plantar violação).
- **Operacional (manual, documentado no caderno de testes):** AC9 — `docker compose up`, abrir Grafana,
  conferir alvo do Prometheus e datasources/dashboard. Não entra no `./mvnw verify`.

## Open Questions

- **Limiares de alerta** (drift, erro %, latência): quem opera (SRE/dono) define os valores reais; a
  stack sobe pronta para receber regras, mas nenhuma regra é inventada (fora de escopo desta fase).
- **Tracing distribuído**: só se um dia houver saltos de rede a correlacionar (extração de
  microsserviço) — hoje o monólito não justifica (Regra Zero); gancho documentado, sem dívida.
- **Retenção/persistência de métricas e logs em produção** (volume, storage): decisão de infra do dono;
  os defaults da POC (Loki 168h, filesystem) servem ao dev/POC e são parametrizáveis.

## Notes

- **Camadas (ADR 0012):** toda a fiação de observabilidade vive em `com.fksoft.infra.observability`
  (filtro de correlation id já existente; novo listener de métricas de negócio; config do Actuator) e
  em `com.fksoft.application.api` (apenas o `VersionController`). O `domain` permanece puro.
- **Eventos instrumentados (já existentes):** `BookingConfirmed`, `BookingCancelled`, `QuoteComposed`,
  `PriceOverridden`, `CommissionInvoiceIssued`, `PeriodClosed`, `UserAuthenticated` — todos já
  publicados pelas fases anteriores. A lista pode crescer sem mudar o contrato (métrica é aditiva).
- **Versão:** capability retro-compatível nova ⇒ **MINOR** ⇒ `0.22.0` (pom.xml + OpenAPI), tag `0.22.0`
  (ADR 0015).
```
