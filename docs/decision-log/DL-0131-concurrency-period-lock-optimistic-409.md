# DL-0131 — Concorrência: lock do período no register/post + conflito otimista → 409

- **Fase:** 19i (Refactoring de maturidade — QA hardening)
- **Spec(s):** SPEC-0015 (BR4 revisado), SPEC-0017 (BR2/BR3 provados), SPEC-0028 (BR9/BR10)
- **ADR relacionado:** 0011 (contrato de erro), 0012 (camadas)
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

Dois furos de concorrência reais, achados ao escrever os testes que o plano da 19i pedia:

1. **`FinanceService.register`/`postFromCharge` liam o período SEM lock** enquanto `closePeriod`
   sela com `findByIdForUpdate`. Entre a leitura (OPEN) e o insert, o fechamento podia commitar —
   e a entrada **escorregava para o período recém-selado**, violando o BR4 ("nunca lançar em
   período CLOSED"). O teste de corrida provou o furo: sem o lock, a entrada entra e nenhuma
   exceção é lançada.
2. **Conflito otimista (`@Version`) virava 500**: `OptimisticLockingFailureException` caía no
   handler catch-all (`error.internal`) — o chamador não tinha como saber que era só recarregar e
   tentar de novo.

## Decisão

1. **`register` e `postFromCharge` tomam o MESMO lock de linha do período** (`findByIdForUpdate`)
   que o `closePeriod`: um lançamento correndo contra o fechamento **serializa** — ou entra antes
   do selo, ou relê CLOSED e é rejeitado/pulado. Custo: um lock de linha por lançamento no
   registro do período — irrelevante nesta escala e correto por construção.
2. **Handler global mapeia `OptimisticLockingFailureException` → 409 `error.conflict`**
   (i18n pt/en): "recarregue e tente de novo" vira contrato, não acidente.
3. **Testes de corrida determinísticos** (latch + `TransactionTemplate`, sem sleep especulativo
   além da janela controlada): fechamento segura o lock → register bloqueia → relê CLOSED → 409
   de domínio; e N threads no `beginInstallmentExecution` do payout começam **exatamente uma**
   execução (lock pessimista já existia — agora está provado).

## Justificativa

- O furo do register×close era o clássico check-then-act sem lock; o fix reusa o lock que já
  existia no caminho do fechamento (nenhuma peça nova — Regra Zero).
- **Regressão vermelho→verde documentada**: com o lock revertido, o teste falha ("Expecting code
  to raise a throwable" — a entrada entrou no período selado); com o lock, passa e o banco
  termina com 0 entradas no período CLOSED.
- 409 para conflito otimista é o mapeamento HTTP canônico (RFC 9110: o estado atual do recurso
  conflita com a escrita) e o que o frontend já sabe tratar (recarregar).

## Alternativas descartadas

- **Constraint/trigger no banco impedindo insert em período CLOSED:** duplicaria a regra em SQL;
  o lock de linha resolve no mesmo lugar da regra.
- **Retry automático do conflito otimista no servidor:** esconderia escrita perdida; a decisão de
  repetir é do cliente (que revê o estado).

## Impacto

- **Arquivos:** `FinanceService` (2 pontos → `findByIdForUpdate`), `GlobalExceptionHandler`
  (+handler 409), `messages(.pt_BR).properties` (+`error.conflict`).
- **Testes:** `FinanceClosePostRaceIntegrationTest` (regressão do furo),
  `PayoutDoubleExecuteRaceIntegrationTest`, `GlobalExceptionHandlerTest` (+409),
  `FinancePeriodTimezoneIntegrationTest` + `SupportCaseSlaWindowTest` (blindagem de fuso — BR11).
- **Contratos:** nenhum shape muda; um 500 acidental vira 409 documentado (OpenAPI global já
  lista 409).

## Como reverter

Barata: remover o lock e o handler (os testes de corrida ficariam vermelhos — é o ponto).
