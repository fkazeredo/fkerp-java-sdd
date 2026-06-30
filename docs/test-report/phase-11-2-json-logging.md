# Caderno de testes — Fatia 11-2 · Logs estruturados em JSON

## Escopo

SPEC-0027 **BR5** (logs JSON com correlation id, sem segredo/PII) e o aceite operacional do AC9 (a
coleta dos logs JSON pelo Alloy → Loki, verificada na fatia 11-3). DL-0096.

## Decisão materializada (DL-0096)

- **Logging estruturado nativo do Spring Boot** (3.5.16 traz o `ElasticCommonSchemaStructuredLogFormatter`):
  no container, `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` faz a saída ser **JSON (ECS)**; o dev/local
  segue com o console humano (`logging.pattern.console`, com `[%X{correlationId}]`).
- O **correlation id** já vive no MDC (`CorrelationIdFilter`, fatia da Fase 0); o formatador ECS
  serializa o MDC, então o `correlationId` aparece como **campo do JSON** sem código novo.
- **Mascaramento = higiene na origem** (security.md): não se introduz encoder de mascaramento; a
  regra é "não logar segredo/PII". Coberto por teste de regressão no caminho mais sensível (login).

## Casos de teste

### Integração (Testcontainers Postgres)

`SensitiveDataNotLoggedIntegrationTest`:
- **loginNeverLogsThePasswordOrTheToken** — faz um login real (`it`/`dev12345`), captura **todos** os
  eventos de log (root logger via `ListAppender`) e afirma que **nenhum** evento (mensagem + MDC)
  contém a senha submetida nem o JWT emitido; ainda confirma que houve a linha `result=SUCCESS` (a
  asserção inspecionou saída real, não lista vazia). → **BR5**.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. **475 testes** (474 + 1 novo), 0 falhas. ArchUnit
16 verdes; Spotless limpo; Checkstyle 0 violações.

```
Tests run: 475, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Cobertura / o que NÃO está coberto

- A **forma JSON** dos logs é configuração nativa do Spring Boot (formatador ECS presente no jar,
  confirmado) ligada por env no container; a verificação visual do JSON no Grafana/Loki é
  **operacional** (AC9, fatia 11-3) — não cabe no `./mvnw verify`.
- O teste cobre o caminho **mais sensível** (login). Os demais módulos já praticam a higiene (ex.:
  Platform nunca loga material de chave; documentos mascaram CNPJ) desde fases anteriores; o teste
  desta fatia trava a regressão no ponto de maior risco.

## Como reproduzir

```bash
cd backend
./mvnw -o test -Dtest='SensitiveDataNotLoggedIntegrationTest'
# Pré-visualizar o JSON localmente:
SERVER_PORT=8080 ./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.structured.format.console=ecs
# No container (docker compose), os logs já saem em JSON (ECS) por LOGGING_STRUCTURED_FORMAT_CONSOLE.
```
