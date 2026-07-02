# DL-0132 — Portões de qualidade elevados: PIT (mutação), jqwik (propriedades) e pisos de BRANCH

- **Fase:** 19i (Refactoring de maturidade — QA hardening)
- **Spec(s):** SPEC-0028 (BR8/BR11/BR12 — gradua o "Out of Scope / Future" da própria spec)
- **ADR relacionado:** 0015 (release), 0012
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A Fase 12 deixou registrado como "futuro": mutation testing e piso de branch. Cobertura de
instrução diz que a linha **rodou**; não diz que os testes **perceberiam** se ela estivesse
errada — em código de dinheiro (centavos, taxas, arredondamento) essa diferença é exatamente onde
mora o prejuízo silencioso. E os testes de exemplo fixo não exploram o espaço de entradas da
matemática de dinheiro.

## Decisão

1. **PIT (pitest 1.17.3 + junit5-plugin 1.2.2) em profile Maven `mutation`**, escopado às classes
   de matemática de dinheiro (`domain.money`, `InstallmentPlan`/`Payout`/`PayoutInstallment`,
   `domain.commissioning`, `FxPosition`/`ForwardContract`/`CurrencyPair`, `LedgerEntry`/
   `AccountingPeriod`) e aos **testes unitários/propriedade rápidos** dos mesmos pacotes
   (integração excluída — sem Spring/Testcontainers por mutante; a corrida fica em minutos).
   `mutationThreshold=60` **falha a execução** abaixo do piso. **Job próprio no CI** com o
   relatório como artefato. Primeira medição: **185 mutantes, 126 mortos (68%), test strength
   89%** — o piso 60 é não-regresso com folga real.
2. **jqwik 1.9.2** (test scope) para invariantes por propriedade: `Money` (normalização escala 2
   HALF_UP, add/subtract inversos, comutatividade, identidade do zero, mistura de moeda proibida)
   e `InstallmentPlan` (soma exata ao centavo para qualquer total×contagem; resto na primeira
   parcela; nunca negativa) — 1000 casos por propriedade a cada build.
3. **Pisos:** JaCoCo ganha **BRANCH ≥ 0,65** (medido 0,689) ao lado do INSTRUCTION ≥ 0,80;
   Vitest sobe statements 65→70 (medido 72,17), lines 65→75 (77,36), functions 48→49 (49,8);
   **branches do Vitest permanece 55** (medido 55,67 — subir 0,67pp seria barra cosmética,
   contra a filosofia da SPEC-0028 BR2).

## Justificativa

- Mutation score é o teste do teste: nos módulos de dinheiro um mutante vivo é um bug de centavos
  que a suíte não pegaria. Escopar ao domínio puro mantém o job em minutos (PIT sobre testes de
  Testcontainers seria inviável e não é onde a mutação paga).
- Propriedades cobrem o espaço de entradas que exemplos fixos não cobrem — e já flagraram a
  necessidade de documentar o espalhamento do resto (bound = contagem−1 centavos na primeira
  parcela, comportamento do DL-0050).
- Pisos seguem a regra da casa: **não-regresso defensável que o código atual passa**, nunca meta
  cosmética.

## Alternativas descartadas

- **PIT no build normal (`verify`):** minutos a mais em todo build local; o job de CI separado dá
  o gate sem o atrito.
- **PIT sobre toda a base:** mutantes em controllers/infra exigiriam os testes de integração →
  horas de build e sinal fraco.
- **Subir branches do Vitest para 56:** viraria quebra na primeira mudança inocente (folga de
  0,67pp); barra cosmética.

## Impacto

- **Arquivos:** `backend/pom.xml` (jqwik, regra BRANCH, profile `mutation`), `ci.yml` (+job
  `mutation` com artefato), `frontend/angular.json` (pisos), testes novos (2 classes de
  propriedade).
- **Contratos:** nenhum.

## Como reverter

Barata: remover profile/regra/pisos. Perderia o gate — não recomendado.
