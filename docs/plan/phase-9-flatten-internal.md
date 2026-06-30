# Plano — Fase 9: Limpeza estrutural (remover `internal` do domain)

> ADR + chore **estrutural**, sem SPEC. Definido por `docs/ROADMAP.md` (linha da Fase 9) e pela
> herança Go (pacotes planos; encapsulação por tooling, não por pasta). Sem mudança de
> contrato/comportamento. Versão: **PATCH 0.20.1** (ADR 0015).

## Objetivo

Achatar `com.fksoft.domain.<módulo>.internal.*` → `com.fksoft.domain.<módulo>.*` em **todos** os
módulos de domínio (main **e** test), removendo a convenção de pacote `internal`. Preservar a
encapsulação **provada** movendo o mecanismo de fronteira: de "pasta `internal` escondida pelo
Spring Modulith" para **marcador de tipo `@ModuleInternal` + regra ArchUnit** (mais Spring Modulith
mantido para ciclos e grafo de módulos).

## Diagnóstico (pesquisa feita antes do plano)

- 22 `package-info.java` com `@ApplicationModule` (20 módulos têm subpacote `internal`; `commissioning`
  e `money`/`error` não — `money`/`error` são kernels não-módulo).
- 126 arquivos `.java` em `*/internal/*` (main); 10 em test. 106 têm tipo público de topo; ~20 são
  package-private (listeners, codecs, guards) — já invisíveis ao compilador.
- **Nenhum** import cross-module de `*.internal.*` hoje (o Modulith já barra). Todas as referências
  a `internal` cross-pacote são: (a) o `*Service` do próprio módulo → seu `internal`; (b) testes do
  próprio módulo; (c) **`infra.security`** → `identity.internal.IdentityUser/Repository` (o adapter
  de segurança opera a persistência do módulo — permitido pela arquitetura, ADR 0010/0012).
- **Risco central:** depois de achatar, a base package vira a *unnamed named interface* do Modulith
  (toda pública = API). O Modulith deixa de esconder os tipos ex-internal. **Logo a encapsulação
  precisa migrar para ArchUnit** (o próprio ROADMAP autoriza `@NamedInterface`/ArchUnit).

## Mecanismo de encapsulação adotado

1. **Marcador `@com.fksoft.domain.ModuleInternal`** (kernel não-módulo, `@Retention(CLASS)`,
   `@Target(TYPE)`): anotado em **cada tipo público** que era `internal` (entidades, repositórios,
   codecs públicos, etc.). Tipos package-private não precisam (o compilador já os esconde).
2. **Nova regra ArchUnit `MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES`**: nenhuma classe
   de **outro** módulo de domínio pode depender de um tipo `@ModuleInternal`. Exceções: o próprio
   módulo (inclui seus testes — mesmo prefixo de pacote) e `..infra..` (a arquitetura permite o
   adapter operar a persistência do módulo — preserva o acesso de `infra.security` a `identity`).
   Esta regra **substitui** o esconde-automático do `internal` do Modulith, agora para os 22 módulos.
3. **Predicados existentes** (`isForeignCommandFacadeOrInternal`,
   `isAnotherModuleCommandFacadeOrInternal`): trocar `pkg.contains(".internal")` por
   `isAnnotatedWith(ModuleInternal.class)` — mantêm Intelligence/Portfolio/Platform com a mesma
   força.
4. **Teeth**: novo fixture + teste em `ArchitectureRulesHaveTeethTest` provando que a regra geral
   **falha** quando um módulo externo toca um tipo `@ModuleInternal`. Os teeth existentes
   (intelligence/platform via `BookingService`) continuam válidos.
5. **Spring Modulith** `verify()` permanece no suite (ciclos, grafo); `@ApplicationModule` fica.
   Os `package-info` atualizam a prosa: API nomeada + convenção `@ModuleInternal` no lugar de
   "o subpacote `internal` é module-private".

## Fatias (cada uma `./mvnw verify` verde antes do merge `--no-ff`)

- **9a — costura transversal:** `ModuleInternal` + regra ArchUnit + fixture/teste de teeth +
  atualização dos predicados. Verde sem ainda mover nada (a regra começa vazia/`allowEmptyShould`).
- **9b..9e — achatar em lotes de módulos** (main+test): mover tipos de `…/internal/` para `…/`,
  apagar a pasta `internal`, anotar os tipos públicos com `@ModuleInternal`, corrigir imports/
  `package-info`. Lotes:
  - 9b: accounts, sourcing, exchange, commissioning(*sem internal*), quoting, commercialpolicy.
  - 9c: booking, reconciliation, compliance, finance.
  - 9d: billing, payout, aftersales, marketing.
  - 9e: portfolio, assets, people, platform, identity, intelligence, admin.
- **9f — fechamento:** pom 0.20.1, release note, CHANGELOG.en-US, test-report+INDEX, ADR/DL/INDEX.

## Definition of Done

- Nenhum pacote `internal` sob `com.fksoft.domain` (main e test). 466 testes verdes (+ os novos de
  teeth). ArchUnit/Modulith/Spotless/Checkstyle verdes.
- Sem migração Flyway, sem mudança de contrato (REST/DTO/JSON/evento publicado/i18n/schema).
- ADR 0016 + DL-0089 + INDEX. MANUAL **não** muda (nada muda para o usuário — Regra Zero).
- Tag 0.20.1; develop e main publicados (main por merge `--no-ff` em detached, árvore == develop).
