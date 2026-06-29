# ADR 0014: Conjunto inicial de módulos e ordem de construção em fatias

## Status

Accepted

## Context

Vamos construir o ERP da Acme Travel a partir deste template de arquitetura e do redesenho de
domínio (`erp-turismo-b2b-redesenho.md`). O redesenho mapeia ~22 bounded contexts. Precisamos
decidir **quais módulos existem no v1** e **em que ordem** construir, sem violar:

- a Regra Zero e *current business need over speculative future need* (`core-principles.md`);
- *New project creation* (`workflow.md`): "MUST NOT create a huge empty architecture with unused
  modules, fake bounded contexts or placeholder classes";
- a natureza viva das specs e o registro (não invenção) de *Open Questions*.

O redesenho já indica o caminho (Parte 12): começar pelo **núcleo comercial 100% manual e
rastreável**, depois compliance mínimo, depois integração, etc. O núcleo manual é também o que tem
**menos perguntas em aberto** (Parte 13), logo o ponto de partida de menor risco.

## Decision

1. **Monólito modular** (já decidido em `core-principles.md`), com fronteiras impostas por ArchUnit
   + Spring Modulith. Microsserviço só com motivo concreto.
2. **Entrega em fatias verticais, test-first**, cada fatia um entregável de ponta a ponta
   (migração → domínio → API → tela quando houver valor de UI). Detalhe operacional em
   `docs/ROADMAP.md` e `docs/TUTORIAL.md`.
3. **Módulos do v1 (Fase 0–1):** `accounts`, `exchange`, `commissioning`, `quoting` (mais
   `commercialpolicy` como **costura fina/stub** para o markup), depois `booking` e
   `reconciliation`. Plus o esqueleto técnico (`infra.web`, `infra.security`, `infra.i18n`,
   `infra.observability`).
4. **Adiados** (cada um entra só quando sua fatia/spec chega): `compliance`, `integration`,
   `intelligence`, `billing`, `payout`, `aftersales`, `marketing`, `portfolio`, `assets`,
   `people`, e os genéricos `finance`/`identity`/`admin`.
5. **Motor de sugestão só em `MANUAL` no v1**; o ramo `INTEGRATED` (preço externo confiável) fica
   adormecido sem dívida (redesenho Parte 4.3).
6. **Costuras adiadas via mock rastreável** (`simulation-and-mocking.md`), referenciando a spec
   dona: precedência de `CommercialPolicy` (Diretiva > Promoção > Contrato > Política > Padrão),
   faixas de override (Q4), escopo da comissão do agente (Q5), exposição/subsídio×drift de câmbio.

## Consequences

**Positivas**
- Sistema executável, testado e com CI desde o Slice 0; cada fase é implantável.
- Regras de arquitetura travadas antes do primeiro código de negócio.
- Perguntas em aberto da Parte 13 não bloqueiam o núcleo — só as fatias que de fato dependem delas.

**Negativas / custo**
- Algumas fachadas/stubs (markup de `CommercialPolicy`) são placeholders a serem substituídos pela
  spec dona — precisam de rastreabilidade (`SPEC-XXXX`) para não virarem dívida silenciosa.
- Colaboração entre módulos via fachadas públicas adiciona uma cerimônia leve frente a chamar
  repositório alheio (proibido por Spring Modulith) — é o preço de preservar extração futura.
- O valor pleno do DSS só aparece na Fase 7.

## Alternatives Considered

- **Especificar tudo no início (as ~22 specs).** Rejeitado: viola `workflow.md` (*New project*) e a
  Regra Zero; specs são *just-in-time* e vivas. Geraria contexto especulativo que envelhece antes
  de ser usado.
- **Microsserviços desde o começo.** Rejeitado: `core-principles.md` manda *Modular Monolith
  First*; não há motivo concreto (deploy independente, isolamento de carga/equipe/falha) no v1.
- **Integração primeiro.** Rejeitado: o redesenho trata o manual como fluxo de primeira classe e de
  menor risco/menos perguntas; o ramo `INTEGRATED` só faz sentido depois que o núcleo comercial
  está provado e há uma ACL real para exercitá-lo (Fase 3).
