# DL-0089 — Encapsulação pós-achatamento via marcador `@ModuleInternal` + ArchUnit

- **Fase:** 9 (Limpeza estrutural — remover `internal` do domain)
- **Spec(s):** nenhuma (ADR + chore); ADR 0016; `docs/ROADMAP.md` (Fase 9)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O ROADMAP (Fase 9) manda achatar `…/<módulo>/internal/` → `…/<módulo>/` e "preservar a encapsulação
via `@NamedInterface`/ArchUnit", **sem dizer qual** dos dois mecanismos (ou combinação) usar. O fato
técnico decisivo: ao achatar, a base package vira a *unnamed named interface* do Spring Modulith e
**toda classe pública nela vira API** — o Modulith deixa de esconder os tipos ex-internal. Logo,
escolher o mecanismo de fronteira não é cosmético: é o que mantém (ou não) a encapsulação **provada**.

## Decisão

Mecanismo = **marcador de tipo `@com.fksoft.domain.ModuleInternal` + regra ArchUnit**, mantendo o
Spring Modulith para ciclos/grafo:

1. `@ModuleInternal` (kernel não-módulo, `@Target(TYPE)`, `@Retention(CLASS)`) em **cada tipo
   público** que era `internal`. Package-private não recebe (o compilador já esconde).
2. Regra ArchUnit `MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES`: nenhuma classe de **outro**
   módulo de domínio depende de um tipo `@ModuleInternal`. Exceções: o próprio módulo (e seus testes)
   e `..infra..` (adapter opera a persistência do módulo — ADR 0010/0012).
3. Predicados existentes (Intelligence/Portfolio/Platform) trocam `pkg.contains(".internal")` por
   `isAnnotatedWith(ModuleInternal.class)`.
4. Teeth: teste novo prova a regra geral falhando quando um módulo externo toca um `@ModuleInternal`.

## Justificativa

- **Regra Zero:** o marcador + 1 regra é a coisa mais simples que recria, para os 22 módulos, o
  esconde-automático que o `internal` tinha. `@ApplicationModule(allowedDependencies)` exigiria cada
  módulo enumerar todas as dependências permitidas (cerimônia alta, frágil) e/ou recriar subpacotes
  de API por `@NamedInterface` — reintroduzindo a divisão de pastas que a fase elimina.
- **`@NamedInterface` é package-scoped:** com API e miolo na mesma base package, não há como separá-los
  por named interface sem voltar a ter subpacotes. O sinal precisa ser **no tipo**.
- **Mantém as teeth (CLAUDE.md Regra 5):** a regra falha ao plantar a violação; os predicados de
  Intelligence/Portfolio/Platform seguem com a mesma força, só trocando o sinal `.internal` pelo
  marcador.
- **Preserva o acesso legítimo de infra:** a exceção `..infra..` mantém `infra.security` lendo a
  persistência de `identity` (ADR 0010/0012) sem furo cross-módulo de negócio.

## Alternativas descartadas

- **Só package-private:** muitos tipos ex-internal são `public` por serem usados pelo `*Service` e
  pelos testes do módulo (pacotes diferentes). Rebaixar acesso quebraria testes/serviço e não daria
  ao adapter de infra acesso à persistência. Não esconde de *outros módulos* expondo ao *próprio*.
- **`allowedDependencies` + `@NamedInterface` por subpacote de API:** cerimônia alta e reintroduz
  subpacotes — contra a Regra Zero e o objetivo da fase.
- **Confiar só no Spring Modulith pós-achatar:** tecnicamente afrouxaria o portão — o Modulith não
  esconde mais os ex-internal; a encapsulação sumiria (Regra 5).

## Impacto

- **ADR:** 0016 (decisão de achatar + mecanismo). **Specs:** nenhuma.
- **Arquivos:** novo `domain/ModuleInternal.java`; `ArchitectureTest` (nova regra + predicados
  atualizados); `ArchitectureRulesHaveTeethTest` + fixture novo; 126 arquivos main + 10 test movidos
  de `internal/` para a base package, com `@ModuleInternal` nos públicos; 20 `package-info`
  atualizados (prosa). **Migração:** nenhuma. **Contrato:** nenhum (REST/DTO/JSON/evento/i18n/schema
  inalterados).
- **Modulith:** sem novo módulo; `@ApplicationModule` e `verify()` mantidos.

## Como reverter

Reversão **moderada** (mecânica): regenerar os subpacotes `internal`, mover os tipos de volta,
remover `@ModuleInternal`/a regra ArchUnit e restaurar os predicados para `pkg.contains(".internal")`.
O diff é amplo, mas determinístico e protegido pelos testes + gates. Não há dado/contrato a migrar.
