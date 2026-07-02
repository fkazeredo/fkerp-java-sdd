# Fase 19i — QA hardening (concorrência, mutação, propriedades, timezone, pisos)

Data: 2026-07-02 · Versão: 0.41.0 · Specs: SPEC-0028 (BR8–BR12), SPEC-0015 (BR4 revisado) ·
DLs: DL-0131, DL-0132

## O que foi endurecido

| Frente | Evidência |
|---|---|
| **Corrida register×close (furo REAL achado e fechado)** | `FinanceClosePostRaceIntegrationTest`: **vermelho antes do fix** (sem o lock, a entrada escorregava para o período recém-selado — "Expecting code to raise a throwable") → `register`/`postFromCharge` passam a tomar o mesmo lock de linha do período que o `closePeriod` → **verde** (0 entradas no período CLOSED; register bloqueia e é rejeitado com `finance.period.closed`). |
| **Double-execute do payout** | `PayoutDoubleExecuteRaceIntegrationTest`: 4 threads em `beginInstallmentExecution` → **exatamente 1** começa (lock pessimista), 3 recebem a rejeição de domínio; banco confirma 1 parcela EXECUTING. |
| **Conflito otimista → 409** | `GlobalExceptionHandlerTest`: `OptimisticLockingFailureException` responde **409 `error.conflict`** (antes: 500 `error.internal` via catch-all). |
| **Timezone** | `FinancePeriodTimezoneIntegrationTest`: período deriva de `occurredAt` **em UTC** com default São Paulo (01:30Z de 1º/fev → período `2031-02`, não janeiro) e Moscou (23:30Z de 31/jan → `2031-01`). `SupportCaseSlaWindowTest`: janela de 72h vira **no segundo seguinte** ao deadline sob qualquer fuso default. |
| **Propriedades (jqwik 1.9.2)** | `MoneyPropertyTest` (6 propriedades × 1000 casos): normalização escala 2 HALF_UP, add/subtract inversos exatos, comutatividade, identidade do zero, moedas não misturam. `InstallmentPlanPropertyTest` (2 × 1000): a divisão em parcelas soma **exata ao centavo** para qualquer total (1 centavo–R$ 100 mi) × qualquer contagem (1–48); resto concentra na primeira parcela; nunca negativa. |
| **Mutation testing (PIT 1.17.3)** | Profile `mutation` sobre as classes de matemática de dinheiro com os testes unitários/propriedade (sem Spring por mutante): **185 mutantes, 126 mortos = 68%** (test strength **89%**; 44 sem cobertura unitária). `mutationThreshold=60` quebra abaixo do piso. **Job próprio no CI** (`ci.yml` → `mutation`) com relatório como artefato. |
| **Pisos elevados** | JaCoCo: +regra **BRANCH ≥ 0,65** (medido **0,689**) ao lado de INSTRUCTION ≥ 0,80. Vitest: statements 65→**70** (medido 72,17), lines 65→**75** (77,36), functions 48→**49** (49,8); branches mantém 55 (medido 55,67 — folga de 0,67pp, subir seria cosmético). |

## E2E (critério de aceite da fatia)

- Corrida local completa: `e2e:up` (stack isolado, Postgres efêmero tmpfs, portas 4201/8081) →
  `npm run e2e` → **24/24 verdes (12,7s)** → `e2e:down` (rede/volume removidos; banco de dev
  intocado — AC8/DL-0101).
- Correção de suíte: `platform-people.spec.ts` tinha união de locators frágil
  (`A.or(B.first())` → violação de strict mode quando tabela populada convive com empty-state de
  outra seção); corrigido para `.or(...).first()` na união. **Não** era regressão do app.
- Aprendizado documentado: a suíte pressupõe **banco virgem por corrida** (empty states genuínos —
  DL-0101); rodar `e2e` duas vezes no mesmo stack falha `accounts.spec.ts` por design. O fluxo é
  sempre up → e2e → down (como no `e2e.yml`).

## Gates finais

- Backend `./mvnw verify` (com BRANCH gate novo) — verde, **586 testes** (571→586, +15);
  INSTRUCTION 90,1% / BRANCH 69,1%.
- PIT `-Pmutation` — verde (68% ≥ 60).
- Frontend `ng lint` / `ng test` (287, pisos novos) / `ng build` — verdes.
- E2E 24/24 verde em stack limpo.
