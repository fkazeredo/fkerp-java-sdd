# Plano — Fase 14: Upgrade de stack (Spring Boot 3.5 → 4.x) · ADR (avaliação) + chore

> **ADR + chore**, **sem SPEC** (infra). O ROADMAP (linha da Fase 14) manda **avaliar** 3.5.16 → 4.x
> (Spring Framework 7, Spring Modulith 2.x), `ngx-graph` só se necessário, e **só executar com gates
> verdes (DL-0002)**. A avaliação pode terminar em **(A) upgrade feito** ou **(B) avaliado e adiado** —
> as duas são entregas completas. Decisão honesta pelo que **fica verde**, sem afrouxar portão (Regra 5).

## Objetivo

Decidir, com base no que realmente passa nos portões, se o backend migra de **Spring Boot 3.5.16**
para **Spring Boot 4.x** (Spring Framework 7 + Spring Modulith 2.x), mantendo **todos os gates verdes**
(537 testes backend, ArchUnit, Spring Modulith, Spotless, Checkstyle, JaCoCo ≥ 0,80; frontend
lint/test/coverage/build; E2E). Registrar a avaliação, a decisão, os bloqueios concretos e o
custo/rollback num ADR; sub-decisões no decision-log; atualizar/fechar **DL-0002**.

## Linha de base (medida antes de qualquer mudança)

- `cd backend && ./mvnw verify` **verde** em **Spring Boot 3.5.16**: **537 testes, 0 falhas, 0 erros**;
  JaCoCo INSTRUCTION ≈ **89,4 %** (26918/30119) — acima do piso 0,80. Java 21.0.11, Maven 3.9.16,
  Docker 29.5.3 (Testcontainers Postgres 16-alpine). Esse é o **último ponto verde garantido**.

## Pesquisa de compatibilidade (antes da decisão)

- **Spring Boot 4.0 GA** em 2025-11-20; linha 4.0.x madura (último patch **4.0.7**, 2026-06-10);
  4.1.0 recém-saída (2026-06-10). Baseline Java 17+ (Java 21 OK), Jakarta EE 11, **Jackson 3 como
  default** (`tools.jackson`), Spring Framework 7.
- **Spring Modulith 2.0 GA** em 2025-11-21; pareia com Boot 4 (último 2.0.x = **2.0.7**, casa com Boot
  4.0.7).
- **Mudanças que tocam este projeto:** renome de starters (`starter-web`→`starter-webmvc`,
  oauth2 com prefixo `security-`), **Jackson 3 default** (22 usos de `com.fasterxml.jackson` em 17
  arquivos main+test), **Testcontainers BOM não mais gerido** pelo parent do Boot 4 (precisa import
  explícito), Flyway exige `spring-boot-starter-flyway` próprio, `@MockBean`/`@SpyBean`→`@MockitoBean`
  (**não usados aqui** — sem impacto). Ponte oficial: `spring-boot-starter-classic` restaura o
  classpath pré-4.0 (Jackson 2 disponível) para migração incremental.
- **springdoc-openapi** é o ponto sensível: a linha 3.0.x mira Boot 4, mas há issues abertas de atrito
  Jackson 3 (#3157/#3175/#3200). Verificável só no build.
- **`ngx-graph` (`@swimlane/ngx-graph`)**: **não há** referência no frontend e **não há** requisito de
  workflow configurável (a POC reverteu o motor de workflow por custo — Regra Zero). **Não trazer.**

## Estratégia incremental (spike → decisão)

1. **Spike numa branch descartável** (`spike/14-sb4`, fora da integração): subir o parent para 4.0.7,
   Modulith 2.0.7, springdoc 3.0.3, importar o Testcontainers BOM, e adicionar
   `spring-boot-starter-classic` (mantém Jackson 2 e o agregado de starter pré-4.0, evitando tocar os
   17 arquivos Jackson e o renome de starter na primeira passada). Rodar `test-compile` → `verify`.
2. **Ler os portões:** se `verify` fica **verde** sem afrouxar nada, seguir para **(A)**; consolidar a
   mudança na integração com commits pequenos. Se um passo não fica verde e não dá para corrigir
   dentro do razoável (ex.: springdoc/Jackson 3 derruba o startup, ou um gate quebra), **reverter o
   spike** e pivotar para **(B)** — manter 3.5.x verde e documentar os bloqueios concretos.
3. **Frontend:** independente do stack do backend; rodar `npm run lint`/`test`/`build` para confirmar
   que continua verde (Fase 14 não toca Angular). E2E conforme ambiente.

## Versionamento (ADR 0015)

- **(A)** upgrade sem mudança de contrato ⇒ **PATCH 0.23.1** (manutenção interna), tag `0.23.1`.
- **(B)** ADR-only, sem mudança de código ⇒ **sem bump** (chore de docs/ADR, como a Fase 15) — sem tag.
  Se entrarem bumps seguros e sem efeito de comportamento, um PATCH 0.23.1 é aceitável.

## Definition of Done

- O stack final (3.5.x ou 4.x) está **verde**: 537 testes, ArchUnit/Modulith/Spotless/Checkstyle,
  JaCoCo ≥ 0,80; frontend lint/test/coverage/build; E2E conforme rodado. **Nenhum gate afrouxado.**
- **ADR 0017** (avaliação + decisão + bloqueios + rollback) e **DL-0108**; **DL-0002 atualizado**
  referenciando o ADR (e fechado/mantido conforme a decisão). INDEX atualizado.
- Sem migração Flyway (sem schema). Sem mudança de contrato pretendida (infra). MANUAL/MANUAL.en-US
  **não** mudam num upgrade puro de stack (Regra Zero) — salvo se algo virar visível ao usuário.
- Caderno de testes em `docs/test-report/` (o que foi verificado/atualizado ou por que adiou).
- Release note + CHANGELOG.en-US **só se** uma versão for cortada (outcome A / bumps seguros).
