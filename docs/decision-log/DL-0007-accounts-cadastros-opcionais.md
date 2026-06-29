# DL-0007 — Accounts: cadastros (CADASTUR/IATA) opcionais no v1

- **Fase:** 1 (Núcleo comercial manual)
- **Spec(s):** SPEC-0002
- **ADR relacionado:** 0014 (conjunto de módulos)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0002 deixa em aberto (*Open Questions*) **quais cadastros (CADASTUR, IATA) são
obrigatórios por tipo de conta**. Sem resposta do negócio, o `register` precisa decidir se
exige algum desses campos para CNPJ/MEI/CPF.

## Decisão

**Nenhum cadastro externo é obrigatório no v1.** Os únicos campos obrigatórios de uma Account
são `legalType`, um `documentNumber` estruturalmente válido (BR2) e único (BR3), e um
`displayName` não-vazio (BR4). `cadastur` e `iata` são **opcionais** e guardados **como vieram**,
sem validação contra registro externo (BR5).

## Justificativa

- O redesenho não fixa obrigatoriedade de CADASTUR/IATA por tipo; o `docs/ROADMAP.md`
  ("Recomendações") não traz recomendação específica para esta pergunta.
- Exigir um cadastro sem uma regra de negócio confirmada **inventaria regra** — proibido
  (`CLAUDE.md`, invariante 3). A SPEC-0002 já modela ambos como opcionais.
- Mantém a fatia simples (Regra Zero) e desbloqueia o restante da Fase 1; quando o negócio
  definir obrigatoriedade (ex.: CADASTUR para agência CNPJ), entra como validação condicional.

## Alternativas descartadas

- **Exigir CADASTUR para contas CNPJ/MEI (agências).** Descartado: sem confirmação do dono é
  suposição de negócio; agências em formação podem ainda não ter CADASTUR ativo.
- **Validar CADASTUR/IATA contra registro oficial.** Descartado: explicitamente fora de escopo
  na SPEC-0002 (integração externa é outra fatia).

## Impacto

- Arquivos: `Account` (campos `cadastur`/`iata` nullable), `AccountService.register`,
  `CreateAccountRequest` (sem `@NotBlank` nesses campos), `V2__create_accounts.sql` (colunas
  nullable).

## Como reverter

Adicionar validação condicional por `legalType` (ex.: `account.cadastur.required`) no domínio +
chave i18n + teste. Mudança localizada e barata.
