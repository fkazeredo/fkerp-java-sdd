# DL-0090 — Stack de UI da Fase 10: PrimeNG 21 (Aura) + Tailwind v4 + @angular/cdk — gradua DL-0003

- **Fase:** 10 (UX & Frontend profissional)
- **Spec(s):** SPEC-0026
- **ADR relacionado:** 0008 (Frontend Stack — alvo); gradua **DL-0003** (Fase 0 adiou PrimeNG/Tailwind)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0026 (nova) pede "elevar o frontend ao padrão do fkerp-poc". O ROADMAP (Fase 10) já nomeia
o destino — PrimeNG 21 (preset **Aura** via `@primeuix/themes`) + primeicons + `@angular/cdk` +
**Tailwind v4** —, mas **não fixa as versões exatas nem como as duas camadas CSS coexistem** sem
brigar (PrimeNG estiliza componentes; Tailwind estiliza layout). DL-0003 deixou PrimeNG/Tailwind
explicitamente adiados; falta a decisão de **graduá-los** e em que versões.

## Decisão

1. **PrimeNG 21** (`primeng@^21`) + **`@primeuix/themes`** com preset **Aura**, configurado via
   `providePrimeNG({ theme: { preset: Aura, options: { darkModeSelector: '.app-dark', cssLayer:
   { name: 'primeng', order: 'theme, base, primeng' } } } })`. **primeicons** para ícones.
2. **`@angular/cdk`** (mesma major do Angular, `^22`) para `A11yModule` (FocusTrap, LiveAnnouncer)
   e `Overlay`/`Dialog` quando o componente PrimeNG não cobrir — base de acessibilidade.
3. **Tailwind v4** (`tailwindcss@^4` + `@tailwindcss/postcss`) integrado por **camadas CSS**
   (`@layer tailwind-base, primeng, tailwind-utilities;`) para o PrimeNG (na camada `primeng`)
   ganhar de Tailwind base mas as utilities de Tailwind ainda poderem ajustar layout. Tailwind
   cuida de **layout/grid do shell**; PrimeNG cuida do **miolo dos componentes**. (ADR 0008:
   "PrimeNG owns component internals; Tailwind owns layout".)
4. **Zoneless mantido.** PrimeNG 21 roda em apps zoneless (Angular 22). Nada de `zone.js`.

## Justificativa

- **ROADMAP "Recomendações"/Fase 10** já recomenda exatamente este conjunto ("PrimeNG 21 preset Aura
  via `@primeuix/themes` + primeicons + `@angular/cdk`; Tailwind v4 por camadas CSS"). Adotar a
  recomendação é o passo 1 do modo autônomo (RUN-PHASE §Modo autônomo).
- **ADR 0008** já fixou PrimeNG + Tailwind como **alvo**; DL-0003 só **escalonou** a entrada pela
  necessidade. A Fase 10 é a "primeira tela real repaginada ao padrão profissional" — é o gatilho
  previsto para graduar.
- **Camadas CSS** são o mecanismo oficial do PrimeNG v21 + Tailwind v4 para coexistir sem `!important`
  (docs PrimeNG "Tailwind CSS" + Tailwind v4 `@layer`). Resolve o "negative" do ADR 0008 (duas
  abordagens de CSS lado a lado) de forma governada.
- **Estabilidade (delivery.md):** PrimeNG 21 e Tailwind 4 são releases estáveis (não RC/snapshot),
  compatíveis com Angular 22 / Node 22.

## Alternativas descartadas

- **Angular Material:** ADR 0008 já o descartou (menos baterias p/ CRUD admin; menos rico em
  DataTable/Calendar pt-BR). Reabrir seria contrariar o ADR sem fato novo.
- **Só Tailwind + componentes à mão:** multiplicaria o trabalho dos CRUDs sem retorno (ADR 0008).
- **PrimeNG sem Tailwind:** layout do shell SaaS (sidebar/topbar/grid responsivo) é exatamente onde
  utility-first ganha; o ROADMAP pede as duas.
- **Tailwind v3:** v4 é o estável atual, com `@layer` nativo e integração documentada com PrimeNG 21;
  ficar na v3 adiaria um upgrade sem ganho.

## Impacto

- **Specs:** SPEC-0026 (Business Rules citam o stack). **DL-0003:** marcado **GRADUADO** (a entrada
  de PrimeNG/Tailwind, que ela adiou, acontece aqui).
- **Arquivos:** `frontend/package.json` (+ primeng, @primeuix/themes, primeicons, @angular/cdk,
  tailwindcss, @tailwindcss/postcss), `frontend/.postcssrc.json`, `frontend/src/styles.scss`
  (camadas + tokens), `frontend/src/app/app.config.ts` (`providePrimeNG`/`provideAnimationsAsync`).
- **Migração/Contrato:** nenhum (mudança só de frontend; sem REST/DTO/JSON/evento/schema).

## Como reverter

Remover as libs do `package.json`, o `providePrimeNG`, as camadas CSS e voltar ao design plano da
Fase 0. **Moderada** porque as telas passam a usar componentes PrimeNG (o diff de templates é amplo,
mas determinístico e coberto por testes/lint/build). Não há dado nem contrato a migrar.
