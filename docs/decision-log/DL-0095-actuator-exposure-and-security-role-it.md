# DL-0095 — Exposição do Actuator: health/info/version públicos; prometheus/metrics atrás de ROLE_IT

- **Fase:** 11 (Observabilidade & monitoramento)
- **Spec(s):** SPEC-0027 (BR1, BR2, BR3)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A POC abre **todos** os `/actuator/**` com `permitAll()` (ela trata o actuator como interno/rede). A
tarefa da Fase 11 manda **expor `health`/`info`/`prometheus` deliberadamente** e **proteger** os
endpoints sensíveis (não vazar `env`/`heapdump`/`beans` publicamente), decidindo a exposição e a auth
pelo modelo de papéis do 8k. Faltava decidir: (a) **quais** endpoints expor; (b) **quem** acessa o
`/actuator/prometheus` e o `/actuator/metrics` (rede-only × papel); (c) o que fica público.

## Decisão

1. **Exposição mínima** (`management.endpoints.web.exposure.include`): **apenas** `health, info,
   prometheus, metrics`. `env`, `beans`, `heapdump`, `threaddump`, `loggers`, `configprops`,
   `mappings`, etc. **não** entram no include (ficam 404).
2. **Público:** `/actuator/health`, `/actuator/health/**` (liveness/readiness), `/actuator/info` e
   `GET /api/version` — probes e metadados de build, sem segredo.
3. **Protegido por `ROLE_IT`:** `/actuator/prometheus` e `/actuator/metrics` (e qualquer outro
   endpoint sensível de operação que venha a ser exposto). O papel **`ROLE_IT`** (TI) já existe
   (SPEC-0024) e é o dono natural de operação/monitoramento. Sem token → **401**; token sem `ROLE_IT`
   → **403** (contrato de erro estável; mesmas handlers `RestAuthenticationEntryPoint`/
   `RestAccessDeniedHandler`).

A regra é adicionada ao `SecurityConfig.configure(...)` (a mesma cadeia real usada em produção e em
teste), antes da regra genérica `/actuator/health*` pública, garantindo precedência correta dos
matchers.

## Justificativa

- **Tarefa da fase (autoridade máxima):** pede explicitamente exposição deliberada + proteção dos
  sensíveis + decidir auth pelo modelo de papéis do 8k. `ROLE_IT` é o papel de TI/operação.
- **Documentação oficial do Spring Boot Actuator:** recomenda **não** expor por padrão endpoints que
  revelam configuração/ambiente; expor só o necessário e proteger com Spring Security. `health`/`info`
  são os candidatos naturais a público (probes/k8s); `prometheus` carrega telemetria operacional que
  não deve ir a anônimos.
- **security.md (projeto):** o backend é a autoridade final; nada de confiar no frontend; não vazar
  internals. `ROLE_IT` mantém a telemetria com quem opera.
- **Sobre "rede-only" (alternativa):** num compose local o Prometheus fala com o `backend` na rede
  interna; em produção a malha/ingress poderia restringir por rede. Mas isso é decisão de **infra do
  dono** e não é testável no `./mvnw verify`. Proteger por **papel** é defensável, testável (401/403)
  e não impede a alternativa de rede depois (as duas se somam).

## Alternativas descartadas

- **`permitAll()` em todo `/actuator/**` (como a POC):** vazaria `/actuator/prometheus` (telemetria) a
  qualquer um se um endpoint sensível fosse incluído por engano; contra a instrução de "proteger os
  sensíveis". A POC aceita porque trata actuator como interno; aqui há modelo de papéis pronto.
- **Expor `metrics` com detalhe a todos:** `metrics` lista nomes de métricas e tags — informação de
  operação; fica com `ROLE_IT`.
- **Criar um papel novo `ROLE_MONITORING`:** Regra Zero — `ROLE_IT` já existe e descreve "TI — dispara
  jobs/crawler e custodia certificado"; monitorar é a mesma função. Não justifica novo papel/migração.
- **Proteção só por rede (network-only), sem papel:** não é exercitável nos testes e depende de infra
  externa; adotamos papel agora, rede fica como reforço de produção (não conflita).

## Impacto

- **Specs:** SPEC-0027 BR1/BR2/BR3, AC2–AC6.
- **Arquivos:** `application.yml` (`management.endpoints.web.exposure.include`,
  `management.metrics.tags.application`); `infra/security/SecurityConfig.java` (matchers
  `/actuator/prometheus`, `/actuator/metrics` → `hasRole("IT")`; manter `/actuator/health*` e
  `/actuator/info` públicos; adicionar `/api/version` aos públicos).
- **Migração/Contrato:** nenhuma migração. OpenAPI ganha `/api/version` (DL-0097).
- **Testes:** `ActuatorExposureIntegrationTest` (401/403/200 + env/beans 404).

## Como reverter

Trocar o `hasRole("IT")` dos matchers de `prometheus`/`metrics` por `permitAll()` (ou restringir por
rede no compose/ingress) e ajustar os testes de 401/403. Mudança localizada no `SecurityConfig` e no
`application.yml` — **barata**.
