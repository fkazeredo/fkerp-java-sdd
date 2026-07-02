# DL-0128 — Spring Boot 4.1 + Jackson 3 (remove a ponte `starter-classic`)

- **Fase:** 19f (Refactoring de maturidade — migração de bibliotecas)
- **Spec(s):** —
- **ADR relacionado:** ADR-0017 (atualizado); substitui a parte "fica em 4.0.x / Jackson 2 via ponte" da DL-0108
- **Data:** 2026-07-02
- **Status:** DECIDIDO (pedido explícito do dono)
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A Fase 14 (ADR-0017/DL-0108) subiu para Boot 4.0.7 mantendo **Jackson 2** via
`spring-boot-starter-classic` (ponte de compatibilidade) e ficou na linha 4.0.x por disciplina de
estabilidade — deixando como **débito rastreado** (a) a migração para Jackson 3 e (b) a subida para
4.1.x. O dono pediu explicitamente **Spring Boot 4.1 + Jackson atualizado**.

## Decisão

1. **Spring Boot 4.0.7 → 4.1.0** (parent), **Spring Modulith 2.0.7 → 2.1.0** (pareia com 4.1),
   springdoc 3.0.3 mantido (linha que suporta Boot 4). Java 21 LTS mantido.
2. **Remover `spring-boot-starter-classic`** → produção passa ao **Jackson 3** (`tools.jackson`), o
   default do Boot 4. Os 9 arquivos que usavam `com.fasterxml.jackson.databind` migram:
   - `ObjectMapper` → `tools.jackson.databind.ObjectMapper`;
   - `JsonProcessingException` (checada) → `tools.jackson.core.JacksonException` (**não-checada** no
     v3) — os `catch (IOException)` do parse viram `catch (JacksonException)`;
   - `TypeReference` → `tools.jackson.core.type.TypeReference`;
   - **anotações** (`@JsonIgnoreProperties`) ficam em `com.fasterxml.jackson.annotation` — pacote
     **inalterado** no Jackson 3, então DTOs anotados seguem válidos.
3. **Invariante de não-regressão:** o **snapshot OpenAPI** (drift gate, DL-0126), o **invariante de
   contrato da Fase 18** e a **suíte completa** provam que o JSON de contrato é **idêntico**.

## Justificativa

- Pedido direto do dono (1ª autoridade na ordem de conflito).
- Boot 4.1.0 e Modulith 2.1.0 estão GA e maduros o suficiente (pesquisado no Maven Central em
  2026-07); o ferramental (Testcontainers, Flyway, springdoc) acompanha.
- Remover a ponte elimina uma dependência de compatibilidade e alinha à direção default da stack.
- O risco (serialização) é coberto por gates fortes: se o JSON mudasse, o snapshot falharia.

## Alternativas descartadas

- **Ficar em 4.0.x / manter a ponte (status quo da DL-0108):** contraria o pedido do dono e mantém
  o débito.
- **Migrar Jackson 3 mas ficar em 4.0.x:** meia-medida; o dono pediu 4.1 explicitamente.
- **Trocar todos os adapters para `JsonMapper` builder:** desnecessário — o bean auto-configurado
  `tools.jackson.databind.ObjectMapper` já é injetável; troca-se só o import.

## Descoberta durante a execução — Flyway modular (Boot 4)

Remover o `starter-classic` teve um efeito além do Jackson: no Boot 4 o **auto-config foi
modularizado**, e o `starter-classic` empacotava **todos** os módulos de auto-config — inclusive o
do **Flyway**. Com `flyway-core` cru, o Flyway **deixou de ser auto-configurado** → o schema não era
criado e **303 testes de integração falharam** ("Schema validation: missing table"). Correção: usar
o starter modular **`spring-boot-starter-flyway`** (traz `flyway-core` + o auto-config), mantendo
`flyway-database-postgresql` para o Postgres 16. Lição registrada: ao sair do `classic`, cada
auto-config antes agregado precisa do seu **starter modular** próprio.

## Impacto

- **pom:** parent 4.1.0, `spring-modulith.version` 2.1.0, remoção do `starter-classic`,
  `flyway-core` → `spring-boot-starter-flyway`.
- **Código:** 9 arquivos main + 2 de teste migrados a `tools.jackson`; 1 `catch` de `IOException` →
  `JacksonException` (`PaymentWebhookReceiver`).
- **Contrato:** **nenhuma mudança** (snapshot OpenAPI byte-idêntico; testes verdes).
- **Débito:** DL-0108 (ponte + migração Jackson) **quitado**.

## Como reverter

Moderada: reintroduzir `spring-boot-starter-classic` + voltar o parent a 4.0.7/Modulith 2.0.7 e
reverter os imports para `com.fasterxml.jackson`. Os gates continuam sendo o juiz.
