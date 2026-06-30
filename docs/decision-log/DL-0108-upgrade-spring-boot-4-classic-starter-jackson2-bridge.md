# DL-0108 — Upgrade para Spring Boot 4.0.7 com ponte `starter-classic` (Jackson 2 mantido na produção)

- **Fase:** 14 (Upgrade de stack — Spring Boot 3.5 → 4.x)
- **Spec(s):** nenhuma (ADR + chore de infra); **ADR 0017**; atualiza **DL-0002**; `docs/ROADMAP.md` (Fase 14)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O ROADMAP (Fase 14) manda **avaliar** 3.5.16 → 4.x e **só executar com gates verdes** (DL-0002). Faltava
decidir, com base no que realmente fica verde: (a) **fazer o upgrade ou adiar**; (b) **qual linha**
(4.0.x × 4.1.x); (c) **como tratar o Jackson 3** (novo default do Boot 4) sem reescrever os 22 usos de
Jackson 2 do código de produção; (d) **como destravar** as incompatibilidades de teste do Boot 4
(`TestRestTemplate` relocado, `@AutoConfigureObservability` removido, 422 renomeado) sem afrouxar portão.

## Decisão

1. **Fazer o upgrade (outcome A): Spring Boot 4.0.7 + Spring Modulith 2.0.7 + springdoc 3.0.3.** O spike
   ficou **verde** (`./mvnw verify`: 537 testes, ArchUnit/Modulith/Spotless/Checkstyle, JaCoCo ≥ 0,80)
   com correções mecânicas, sem afrouxar nada — o motivo da DL-0002 para adiar (ferramental imaturo) não
   se sustenta mais.
2. **Linha 4.0.x (4.0.7), não 4.1.0.** 4.0.x é a linha de patch madura; mesma disciplina de estabilidade
   da DL-0002. Modulith 2.0.7 pareia com 4.0.7.
3. **Jackson 2 mantido na produção via `spring-boot-starter-classic`.** A ponte restaura o classpath
   pré-4.0 (Jackson 2 disponível), então os 22 usos de `com.fasterxml.jackson` (codecs jsonb de
   Portfolio/Marketing, `ObjectMapper` de adapters de integração/segurança) **não mudam** — serialização
   de produção idêntica, **sem risco de contrato**. A migração completa para Jackson 3 (`tools.jackson`)
   fica como **débito rastreado** (este DL — ver "O que fica para depois"), não feita agora por Regra
   Zero.
4. **Destravar os testes preservando comportamento:** import de `TestRestTemplate` →
   `org.springframework.boot.resttestclient` + `@AutoConfigureTestRestTemplate` na classe-base (cobre as
   41 subclasses); `@AutoConfigureObservability` → `@AutoConfigureMetrics`
   (`spring-boot-micrometer-metrics-test`) nos 2 testes de métricas; reads `JsonNode` de teste →
   `tools.jackson.databind.JsonNode` (8 arquivos; o cliente HTTP de teste do Boot 4 usa Jackson 3); e a
   constante `HttpStatus.UNPROCESSABLE_ENTITY` → `UNPROCESSABLE_CONTENT` (renome do Spring Framework 7;
   **status 422 na rede inalterado**) em 3 testes + 1 mapeamento de produção.

## Justificativa

- **Pesquisa (fontes oficiais Spring):** Boot 4.0 GA 2025-11-20, último patch 4.0.7 (2026-06-10);
  Modulith 2.0 GA 2025-11-21 (2.0.7 pareia com 4.0.7); baseline Java 17+ (projeto em Java 21), Jakarta
  EE 11, Jackson 3 default. springdoc 3.0.x suporta Boot 4. O fkerp-poc já roda Boot 4 (alinhamento do
  ROADMAP).
- **`starter-classic` é a recomendação oficial de migração incremental** (restaura o classpath pré-4.0);
  entrega o upgrade com **risco mínimo**, sem tocar serialização persistida. Migrar Jackson agora seria
  custo sem ganho de contrato (Regra Zero) e com risco de alterar JSON gravado.
- **Nenhuma correção afrouxou portão (Regra 5):** cada incompatibilidade virou uma correção verde
  (relocação/anotação/renome), não uma exceção, supressão ou teste apagado. O 422 segue 422 na rede.

## Alternativas descartadas

- **Adiar (outcome B), ficar em 3.5.16.** Descartada: o 4.0.7 fica verde com esforço razoável; adiar
  manteria dívida de versão sem ganho.
- **Ir para 4.1.0.** Descartada por ora: recém-GA; 4.0.7 é a linha de patch madura. Subir depois é
  trivial.
- **Migrar produção inteira para Jackson 3 já (sem `starter-classic`).** Descartada (Regra Zero): mexeria
  em 22 usos de Jackson 2 (jsonb persistido, adapters) sem ganho de contrato e com risco de serialização.
- **Trocar `TestRestTemplate` por `RestTestClient`.** Descartada por ora: reescreveria 41 testes por
  estética, não necessidade; a relocação + `@AutoConfigureTestRestTemplate` os mantém verdes como estão.
- **Trazer `@swimlane/ngx-graph`.** Descartada: sem requisito real de workflow configurável (a POC
  reverteu o motor por custo) — Regra Zero.

## Impacto

- **ADR:** 0017 (decisão de upgrade). **Atualiza DL-0002** (versões da stack). **Specs:** nenhuma.
- **Arquivos:** `backend/pom.xml` (parent 4.0.7; Modulith 2.0.7; springdoc 3.0.3; Testcontainers BOM
  explícito 1.21.4; `starter-classic`; deps de teste `resttestclient`/`restclient`/
  `micrometer-metrics-test`); `AbstractPostgresIntegrationTest` (+`@AutoConfigureTestRestTemplate`); 41
  testes (pacote de `TestRestTemplate`); 8 testes (`JsonNode`→Jackson 3); 2 testes
  (`@AutoConfigureMetrics`); 3 testes + `HttpErrorMapping` (`UNPROCESSABLE_CONTENT`); 4 Javadoc
  ("Unprocessable Content"). **Migração Flyway:** nenhuma. **Contrato:** nenhum (REST/DTO/JSON de
  fio/evento/i18n/schema inalterados; 422 segue 422).
- **Versão:** PATCH **0.23.1** (ADR 0015 — manutenção interna, sem contrato).

## O que fica para depois (débito rastreado)

- **Migração completa para Jackson 3** (`tools.jackson`) no código de produção e remoção do
  `spring-boot-starter-classic` — quando houver ganho concreto (ex.: performance/recursos do Jackson 3)
  ou ao subir para uma linha que descontinue a ponte. **Referência de rastreio: este DL-0108** (atende
  CLAUDE.md Regra 6 — sem TODO solto).
- Adoção opcional de `RestTestClient` no lugar de `TestRestTemplate`, fatia a fatia, se trouxer ganho.
- Avaliar subir para Boot 4.1.x quando a linha amadurecer.

## Como reverter

Reversão **moderada**: voltar o parent para 3.5.16, Modulith 1.4.12, springdoc 2.8.17 no `pom.xml`,
remover `starter-classic` + as deps de teste novas + o Testcontainers BOM, e reverter as trocas de
teste (pacote do `TestRestTemplate`, `@AutoConfigureMetrics`→`@AutoConfigureObservability`,
`JsonNode` Jackson 3→2, `UNPROCESSABLE_CONTENT`→`UNPROCESSABLE_ENTITY`). Diff amplo, porém mecânico e
protegido pelos 537 testes + gates. Não há dado/contrato a migrar (a base de dados não muda).
