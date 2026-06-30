# DL-0003 — Stack frontend da Fase 0: Angular 22 + ngx-translate; PrimeNG/Tailwind adiados

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001 (frontend do walking skeleton)
- **ADR relacionado:** 0008 (Frontend Stack)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO → **GRADUADO (Fase 10, 2026-06-30 — ver DL-0090)**
- **Confiança:** Alta
- **Reversibilidade:** Barata

> **Graduação (Fase 10, SPEC-0026):** o adiamento decidido aqui terminou. PrimeNG 21 (preset Aura via
> `@primeuix/themes`) + primeicons + `@angular/cdk` + Tailwind v4 entraram na Fase 10, exatamente como
> o ADR 0008 previa e como esta DL antecipava ("PrimeNG e Tailwind entram quando chegar a primeira tela
> real"). A decisão de stack/versões e da coexistência por camadas CSS está em **DL-0090**. Esta DL
> permanece válida como registro do escalonamento da Fase 0; nada nela foi revertido — foi concluída.

## Lacuna

O ADR 0008 fixa o stack-alvo do frontend: **Angular 22 + PrimeNG + Tailwind +
ngx-translate**. A SPEC-0001 pede apenas `core/ shared/ features/`, `core/http`
(base URL + interceptors + correlation id) e **uma tela** que consome
`/api/system/health`. Não está decidido **quanto** do stack-alvo deve entrar já
no esqueleto.

## Decisão

Na Fase 0, o frontend usa **Angular 22 (standalone + signals)** + **ngx-translate**
(i18n) + **ESLint**. **PrimeNG e Tailwind entram na Fase 1** (SPEC-0002 Accounts —
a primeira tela real, com CRUD/DataTable, que é exatamente o caso de uso do PrimeNG
no ADR 0008).

## Justificativa

- **Regra Zero / `core-principles.md` (current need over speculative need)** e
  `simulation-and-mocking.md`: adicionar duas bibliotecas de UI (PrimeNG, Tailwind)
  para um único *card* de health é complexidade especulativa. O esqueleto prova a
  stack ponta a ponta sem elas.
- **Robustez da fundação:** cada lib a mais é risco de atrito de peer-deps no
  build/CI. PrimeNG ainda está na major 21 (Angular 22 é a major 22), então acoplar
  PrimeNG ao esqueleto adiantaria um possível ponto de incompatibilidade sem ganho.
- **i18n é invariante** (CLAUDE.md DoD; `frontend-angular.md`: texto de usuário
  sempre via mecanismo de i18n), por isso **ngx-translate fica desde já** — é a
  mais leve das três e honra o invariante de i18n também no front. O seam de i18n
  do back (MessageSource + `messages_pt_BR.properties`) já cobre as mensagens de erro.
- O ADR 0008 **continua sendo o alvo**; esta decisão apenas escalona a entrada das
  libs pela necessidade, sem contrariar o ADR.

## Alternativas descartadas

- **Instalar PrimeNG + Tailwind já na Fase 0** — descartada: especulativo (Regra
  Zero) e adiciona risco de build sem tela que justifique.
- **Não usar ngx-translate na Fase 0 (texto inline)** — descartada: violaria o
  invariante de i18n para texto de usuário.
- **Angular versão < 22** — descartada: ADR 0008 fixa Angular 22, que está
  disponível (CLI 22.0.4).

## Impacto

- Arquivos: `frontend/package.json`, `frontend/src/app/core/*`, feature `health`.
- Specs: nenhuma mudança de regra; a entrada de PrimeNG/Tailwind será citada na
  SPEC-0002 quando a fatia chegar.

## Como reverter

Adicionar PrimeNG + Tailwind é `npm install` + config (`tailwind.config`, provider
do PrimeNG) — trivial, daí Reversibilidade=Barata. Esta decisão não cria dívida:
o stack-alvo do ADR 0008 é alcançado incrementalmente.
