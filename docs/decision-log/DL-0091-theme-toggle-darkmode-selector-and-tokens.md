# DL-0091 — Tema claro/escuro: `ThemeService` + `.app-dark` + tokens `--app-*`, persistido

- **Fase:** 10 (UX & Frontend profissional)
- **Spec(s):** SPEC-0026
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O ROADMAP pede "tema claro/escuro (`ThemeService` + tokens `--app-*`)". Falta decidir **como** o
modo escuro é ativado (PrimeNG Aura usa um seletor de classe), **onde** o estado é guardado e qual
o **default**.

## Decisão

1. **Seletor de classe `.app-dark`** no `<html>` (configurado em `darkModeSelector` do preset Aura).
   `ThemeService` (signal) adiciona/remove `app-dark` no `documentElement`.
2. **Persistência** em `localStorage` (`acme.erp.theme` = `light|dark`); na inicialização, se não
   houver valor salvo, segue `prefers-color-scheme` do sistema (default = preferência do SO).
3. **Tokens `--app-*`** (ex.: `--app-bg`, `--app-surface`, `--app-text`, `--app-muted`,
   `--app-border`) definidos em `:root` e sobrescritos sob `.app-dark`, para o shell/telas que não
   são componentes PrimeNG usarem a mesma paleta nos dois temas.
4. **Toggle na topbar** (botão acessível com `aria-pressed`), atalho de teclado e item na paleta.

## Justificativa

- **PrimeNG/Aura oficial:** o `darkModeSelector` é o mecanismo recomendado pelo `@primeuix/themes`
  para alternar tema sem rebuild; usar `.app-dark` alinha o tema dos componentes PrimeNG com os
  tokens do shell numa só troca de classe.
- **`prefers-color-scheme` como default** respeita a expectativa do usuário (WCAG/UX) sem persistir
  nada até ele escolher — depois a escolha manual vence.
- **Reversível barata:** é só um serviço + classe + tokens; remover volta ao tema único.

## Alternativas descartadas

- **Media-query `@media (prefers-color-scheme)` apenas (sem toggle):** o ROADMAP pede toggle
  explícito; só media-query não deixa o usuário forçar o tema.
- **Atributo `data-theme` em vez de classe:** funciona, mas o preset Aura espera um *selector*; classe
  é o caminho documentado e o mais simples.
- **Guardar tema no backend (perfil do usuário):** especulativo (Regra Zero); preferência de UI é
  local ao dispositivo.

## Impacto

- **Arquivos:** `core/theme/theme.service.ts` (+ spec), `app.config.ts` (`darkModeSelector`),
  `styles.scss` (tokens `--app-*` em `:root` e `.app-dark`), shell (botão toggle).
- **Migração/Contrato:** nenhum.

## Como reverter

Remover `ThemeService`, a classe `.app-dark` e os overrides de token. Barata.
