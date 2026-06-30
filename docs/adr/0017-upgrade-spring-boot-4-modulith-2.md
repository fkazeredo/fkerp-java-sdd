# ADR 0017: Upgrade da stack para Spring Boot 4.0 (Spring Framework 7, Spring Modulith 2.0)

## Status

Accepted (Fase 14 — ADR + chore de infra; substitui a decisão de versão da DL-0002; sem mudança de
contrato)

## Context

A `DL-0002` (Fase 0) fixou a stack do backend em **Spring Boot 3.5.16 + Spring Modulith 1.4.12**,
priorizando **estabilidade e LTS** (`delivery.md`) num momento em que a linha 4.x era recém-GA e o
ferramental (Modulith, Testcontainers, Flyway, springdoc) ainda não tinha maturidade comprovada com
ela. A própria DL-0002 registrou que a migração para 4.x seria objeto de **um ADR próprio quando
houvesse motivo concreto** — esta é a Fase 14 do ROADMAP: **avaliar** 3.5.16 → 4.x (Spring Framework
7, Spring Modulith 2.x) e **só executar com os gates verdes** (DL-0002 / Regra 5).

Estado real do ecossistema na data da avaliação (junho/2026), pesquisado antes de decidir:

- **Spring Boot 4.0 GA** em 2025-11-20; linha 4.0.x madura — último patch **4.0.7** (2026-06-10, 77
  correções). 4.1.0 também GA (2026-06-10), mas recém-saída.
- **Spring Framework 7** (base do Boot 4); baseline **Java 17+** (o projeto está em Java 21 LTS — OK),
  **Jakarta EE 11**, **Jackson 3 como serializador default** (`tools.jackson`).
- **Spring Modulith 2.0 GA** em 2025-11-21; último 2.0.x = **2.0.7**, pareando com Boot 4.0.7.
- **springdoc-openapi 3.0.x** mira o Boot 4.
- O **fkerp-poc** (projeto irmão, mais maduro) já roda Boot 4 — alinhamento desejado pelo ROADMAP.

**Linha de base medida antes de mexer:** `./mvnw verify` **verde** em 3.5.16 — **537 testes**, 0
falhas; JaCoCo INSTRUCTION ≈ 89,4 %. Esse é o último ponto verde garantido e o piso de não-regressão.

## Decision

**Migrar para Spring Boot 4.0.7 + Spring Modulith 2.0.7 + springdoc-openapi 3.0.3**, mantendo **todos
os gates verdes** (537 testes backend, ArchUnit, Spring Modulith, Spotless, Checkstyle, JaCoCo ≥ 0,80;
frontend lint/test/coverage/build; E2E). A migração foi feita **incrementalmente num spike
descartável**, com `./mvnw verify` como juiz: cada bloqueio encontrado foi corrigido **sem afrouxar
portão** (Regra 5); só então o spike foi consolidado.

Escolheu-se a **linha 4.0.x (4.0.7)** e **não a 4.1.0**: 4.0.x é a linha de patch madura e mais testada
(mesma disciplina de "estabilidade" da DL-0002); 4.1.0 é recém-GA. Modulith **2.0.7** pareia com
4.0.7. springdoc **3.0.3** é a linha que suporta Boot 4.

### Mudanças concretas exigidas pelo Boot 4 (todas corrigidas, comportamento preservado)

1. **`spring-boot-starter-classic`** adicionado: restaura o classpath agregado pré-4.0 e **mantém
   Jackson 2 disponível**, então os **22 usos de `com.fasterxml.jackson` no código de produção**
   (codecs jsonb de Portfolio/Marketing, `ObjectMapper` dos adapters de integração/segurança)
   **não precisaram mudar** — a serialização da aplicação continua idêntica (sem risco de contrato).
2. **Testcontainers BOM importado explicitamente** (`testcontainers-bom 1.21.4` em
   `dependencyManagement`): o parent do Boot 4 não gere mais a versão do Testcontainers.
3. **`TestRestTemplate` relocado** para `org.springframework.boot.resttestclient.TestRestTemplate`
   (módulo `spring-boot-resttestclient`) e **não mais auto-provido** sob `@SpringBootTest`. Solução:
   deps de teste `spring-boot-resttestclient` + `spring-boot-restclient`, anotação
   `@AutoConfigureTestRestTemplate` na classe-base `AbstractPostgresIntegrationTest` (cobre as 41
   subclasses de uma vez) e troca do pacote do import nos 41 arquivos. **Mesmo comportamento de teste.**
4. **`@AutoConfigureObservability` removido** no Boot 4 (substituído por `@AutoConfigureMetrics`, do
   módulo `spring-boot-micrometer-metrics-test`). Os 2 testes de observabilidade (que só exercem
   métricas/Prometheus, não tracing) passaram a usar `@AutoConfigureMetrics` — mesma intenção
   (reabilitar export de métricas sob `@SpringBootTest`).
5. **Jackson 3 no cliente HTTP de teste.** O `TestRestTemplate` do Boot 4 desserializa com **Jackson
   3**, que não constrói o tipo Jackson 2 `com.fasterxml.jackson.databind.JsonNode` que 8 testes liam
   das respostas. Os reads `JsonNode` de teste migraram para `tools.jackson.databind.JsonNode` (a API
   `.get()/.asText()/.asInt()` é idêntica). É troca **só no lado do teste**; a produção segue Jackson 2.

### O que NÃO mudou

- **Nenhuma mudança de contrato** (REST/DTO/JSON de fio/evento publicado/chave i18n/schema). A
  serialização de produção continua Jackson 2 (via `starter-classic`). **Sem migração Flyway.**
- **Nenhum gate afrouxado, pulado ou apagado.** Os 537 testes, ArchUnit, Modulith, Spotless,
  Checkstyle e o piso JaCoCo 0,80 continuam ligados e capazes de quebrar o build.
- **`ngx-graph` (`@swimlane/ngx-graph`) não foi trazido**: não há referência no frontend nem requisito
  de workflow configurável (a POC reverteu o motor de workflow por custo — Regra Zero; não antecipar).

### Versionamento (ADR 0015)

Upgrade de infra **sem mudança de contrato** ⇒ **PATCH 0.23.1** (manutenção interna), tag `0.23.1`.

## Consequences

**Positivas**
- Backend alinhado ao fkerp-poc e à geração atual do Spring (Framework 7, Jakarta EE 11), com janela
  de suporte mais longa que a linha 3.5.
- Spring Modulith 2.0 (lifecycle de eventos revisado, verificação de estrutura no startup) disponível
  para evoluções futuras.
- Migração **provada pelos portões**: cada incompatibilidade virou correção verde, não exceção.

**Negativas / custo**
- A ponte `spring-boot-starter-classic` é **transitória**: mantém Jackson 2 no runtime. A migração
  completa para Jackson 3 (`tools.jackson`) no código de produção (codecs jsonb, adapters) fica como
  **débito rastreado** (ver DL-0108) — não foi feita agora por Regra Zero (sem ganho de contrato e com
  risco de mexer em serialização persistida sem necessidade).
- Mistura Jackson 2 (produção) × Jackson 3 (cliente de teste) exige atenção: novos testes que leem
  corpo como árvore devem usar `tools.jackson.databind.JsonNode`.
- Dependências de teste novas (`resttestclient`, `restclient`, `micrometer-metrics-test`) e o BOM do
  Testcontainers explícito aumentam levemente a superfície do `pom.xml`.

## Alternatives Considered

- **Adiar (outcome B) e ficar em 3.5.16.** Rejeitado: a avaliação mostrou que o 4.0.7 fica **verde**
  com correções mecânicas e sem afrouxar gate — o motivo concreto da DL-0002 para adiar (ferramental
  imaturo) não se sustenta mais. Adiar seria manter dívida de versão sem ganho.
- **Ir para 4.1.0.** Rejeitado por ora: recém-GA; 4.0.7 é a linha de patch madura (mesma disciplina de
  estabilidade da DL-0002). Subir para 4.1.x depois é um PATCH/MINOR trivial quando amadurecer.
- **Migrar produção inteira para Jackson 3 agora (sem `starter-classic`).** Rejeitado por Regra Zero:
  obrigaria mexer nos 22 usos de Jackson 2 (codecs de jsonb persistido, `ObjectMapper` de adapters)
  sem ganho de contrato e com risco de alterar serialização gravada. O `starter-classic` entrega o
  upgrade com risco mínimo; a migração de Jackson fica como débito explícito (DL-0108).
- **Trocar `TestRestTemplate` por `RestTestClient` (o novo cliente do Boot 4).** Rejeitado por ora:
  reescreveria 41 testes de integração por estética, não por necessidade. A relocação +
  `@AutoConfigureTestRestTemplate` mantém os testes como estão, verdes. `RestTestClient` pode ser
  adotado fatia a fatia depois, se houver ganho.
- **Trazer `ngx-graph`.** Rejeitado: sem requisito real (Regra Zero); a POC reverteu workflow
  configurável por custo.
