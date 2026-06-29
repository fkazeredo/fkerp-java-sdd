# DL-0002 — Versões do stack backend (Spring Boot 3.5, Modulith 1.4, Java 21)

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0001 e os ADRs não fixam as versões concretas de Spring Boot, Spring
Modulith, Maven, Flyway, Testcontainers e ArchUnit. `delivery.md` exige apenas
"estabilidade e LTS; nada experimental/milestone/RC/snapshot em produção".

## Decisão

| Dependência | Versão | Origem |
|---|---|---|
| Java | **21 (LTS)** | toolchain instalado; alinhado ao README |
| Spring Boot | **3.5.16** | última patch estável da linha 3.5 |
| Spring Modulith | **1.4.12** | linha que pareia com Boot 3.5 (via BOM) |
| Maven | **3.9.11** (via wrapper) | ver [[DL-0004]] |
| Flyway | gerenciado pelo Boot (+ `flyway-database-postgresql`) | Boot 3.5 usa Flyway 10/11 |
| Testcontainers | gerenciado pelo Boot (`spring-boot-testcontainers`) | `@ServiceConnection` |
| ArchUnit | **archunit-junit5 1.3.0** | gate de arquitetura |
| Lombok | gerenciado pelo Boot (`optional`) | ADR 0013 |
| Postgres (runtime/teste) | imagem `postgres:16-alpine` | banco de dev/teste |

## Justificativa

- **Spring Boot 3.5.16 em vez de 4.x**: embora a linha 4.x já esteja GA, a 3.5 é
  a mais madura e amplamente testada com todo o ferramental que a fundação
  depende (Spring Modulith, Flyway, Testcontainers, ArchUnit) e com os padrões
  Jakarta/`@Entity` descritos nos ADRs 0011/0012/0013. `delivery.md` manda
  priorizar **estabilidade**. Adotar Boot 3.5 reduz o risco de incompatibilidade
  sutil derrubar a fundação. A migração futura para Boot 4.x é um ADR próprio.
- **Java 21 LTS**: é o JDK instalado e o citado no README; LTS conforme `delivery.md`.
- **Postgres 16-alpine**: versão estável amplamente suportada; alpine reduz o pull.

## Alternativas descartadas

- **Spring Boot 4.0/4.1 (GA)** — descartada por ora: linha mais nova, maior
  superfície de mudança (Spring Framework 7); sem ganho funcional para a fundação.
  Reavaliar em ADR de upgrade quando houver motivo concreto.
- **Spring Boot 3.4.x** — descartada: 3.5.16 é mais recente dentro de uma linha
  igualmente estável.

## Impacto

- Arquivos: `backend/pom.xml` (parent + BOMs + versões), `docker-compose.yml`,
  `backend/Dockerfile`, workflow de CI.
- Migrações/contratos: nenhum.

## Como reverter

Trocar a versão do parent/BOM no `pom.xml` e rodar `./mvnw verify`. Upgrade para
Boot 4.x exigiria revisão de APIs deprecadas e um ADR — daí Reversibilidade=Moderada.
