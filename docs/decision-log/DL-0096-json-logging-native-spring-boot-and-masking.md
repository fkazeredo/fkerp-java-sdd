# DL-0096 — Logs JSON via logging estruturado nativo do Spring Boot (não Logback custom); mascaramento por higiene já praticada

- **Fase:** 11 (Observabilidade & monitoramento)
- **Spec(s):** SPEC-0027 (BR5)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A fase pede **logs estruturados em JSON** com **correlation id** e **dado pessoal/segredo mascarado**,
coletáveis pelo Loki via Alloy. Faltava decidir **como** produzir o JSON: (a) `logback-spring.xml`
custom com encoder Logstash; (b) o **logging estruturado nativo** do Spring Boot 3.4+
(`logging.structured.format`); e **onde** ligar (sempre × só no container).

## Decisão

1. **Usar o logging estruturado nativo do Spring Boot** (3.4+; este projeto está no 3.5.16):
   `logging.structured.format.console` ligado **no container** (perfil/var de ambiente do
   `docker-compose`), sem `logback-spring.xml` custom. Formato **`ecs`** (Elastic Common Schema) —
   chaves padronizadas, amplamente suportado pelo Loki/Grafana.
2. **Correlation id no JSON:** mantém-se o `CorrelationIdFilter` (MDC `correlationId`); o logging
   estruturado nativo serializa o MDC, então o `correlationId` aparece como campo no JSON sem código
   novo. Mantém-se também o padrão de console legível (`logging.pattern.console` com
   `[%X{correlationId}]`) para o **dev local** (sem container).
3. **Mascaramento/segredo:** **não** se introduz um conversor de mascaramento pesado. A higiene de
   "nunca logar segredo/PII desnecessário" **já é praticada** nos módulos (ex.: Platform nunca loga
   material de chave; auditoria grava só metadados; identidade não loga senha/token). A BR5 **reforça**
   essa regra; um teste de fumaça garante que o caminho de login não loga a senha. Campos de segredo em
   configuração (`*.secret`, `*.password`) já vêm de env e nunca são logados.

## Justificativa

- **Regra Zero + fontes oficiais:** o **Spring Boot** documenta o logging estruturado nativo
  (`ecs`/`logstash`/`gelf`) exatamente para "JSON para um coletor de logs" — elimina um
  `logback-spring.xml` custom e a dependência `logstash-logback-encoder`. Menos peça, mesmo resultado.
- **Espelha a POC com a stack daqui:** a POC liga `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` no
  compose; adotamos o mesmo mecanismo nativo (escolhendo `ecs`, igualmente nativo e mais padronizado),
  ligado por env no container e desligado no dev (console humano).
- **Mascaramento proporcional (security.md):** a doc manda "evitar PII desnecessário; mascarar valores
  sensíveis; nunca logar segredos" — higiene de baixo custo, **não** um framework de mascaramento.
  Introduzir um conversor regex global seria over-engineering e poderia mascarar errado; a prática já
  vigente + um teste de fumaça cobrem a BR5.

## Alternativas descartadas

- **`logback-spring.xml` + `logstash-logback-encoder`:** funciona, mas adiciona dependência e XML
  custom para um recurso que o Spring Boot 3.4+ já entrega nativamente. Contra Regra Zero.
- **Conversor de mascaramento global (regex no encoder):** risco de falso-positivo/negativo e custo de
  manutenção; a regra é "não logar", não "logar e mascarar". A higiene na origem é melhor que mascarar
  no fim.
- **JSON sempre ligado (inclusive dev local):** atrapalha a leitura no terminal do desenvolvedor; por
  isso JSON **no container**, console humano **no dev**.

## Impacto

- **Specs:** SPEC-0027 BR5, AC9 (Alloy coleta os logs JSON do container → Loki).
- **Arquivos:** `docker-compose.yml` (env `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` no `app`);
  `application.yml` (mantém o `logging.pattern.console` do dev; comentário explicando o override no
  container). Teste de fumaça de "não loga senha" no caminho de login (reforço de higiene).
- **Migração/Contrato:** nenhum.

## Como reverter

Remover a env do compose (volta a texto plano no container) ou trocar `ecs`→`logstash`/`gelf`
conforme o coletor. Trocar para `logback-spring.xml` custom se um dia precisar de um layout que o
nativo não cobre. Tudo localizado em config — **barata**.
