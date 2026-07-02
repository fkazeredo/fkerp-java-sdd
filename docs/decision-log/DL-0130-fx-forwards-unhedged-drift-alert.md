# DL-0130 — Hedge cambial: forwards manuais + alerta de drift sobre o descoberto (revisa a DL-0027)

- **Fase:** 19h (Refactoring de maturidade — hedge cambial)
- **Spec(s):** SPEC-0032 (nova); SPEC-0011 (BR9 revisado)
- **ADR relacionado:** 0011, 0012, 0014
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0011 **mede** o risco cambial (subsídio × drift × alerta), mas o sistema não tinha o
instrumento que a tesouraria do setor usa para **neutralizá-lo**: o contrato a termo (forward).
Sem cobertura registrada, o alerta de drift (DL-0027) dispara sobre a exposição **total** —
ruído quando parte do livro já está travada com o banco.

## Decisão

1. **`ForwardContract` com registro manual** (sem integração bancária): moeda ISO, nocional,
   taxa contratada, datas, contraparte; máquina de estado `OPEN → SETTLED | CANCELLED`
   (permanece **enum** — critério da Fase 18). Liquidação registra a taxa efetiva e o resultado
   realizado `(settledRate − contractRate) × notional`. Migração **V40**.
2. **Cobertura por moeda**: os nocionais dos forwards OPEN abatem a fração descoberta de cada
   moeda; `LiveExposure` ganha `openForwards` e `unhedgedExposureBase`.
3. **REVISA a DL-0027**: o limiar de 2% passa a incidir sobre a base **descoberta**
   (`unhedgedExposureBase`), e o alerta exige descoberto > 0 — livro 100% coberto nunca alerta.
   O percentual de 2% e o caráter "alerta, não bloqueia" permanecem.
4. **Escritas restritas** a DIRECTOR/FINANCE na matriz 19a (tesouraria).
5. **`HedgeAdvisor` (DSS) adiado para a Fase 20c**: o insight prescritivo "descoberto > limiar →
   sugere hedge" será construído na fatia de **DSS real** (modelos/algoritmos reais, aceitar/
   rejeitar), onde o agregado `Insight` será revisto — hoje ele está acoplado ao
   `PromoFxAssessment` e estender isso agora seria retrabalho certo (Regra Zero).

## Justificativa

- Forward é a prática padrão de tesouraria de operadoras/consolidadoras que compram em moeda
  estrangeira com receita em BRL; travar taxa futura elimina o drift da parcela coberta — logo o
  alerta que ignora a cobertura reporta risco que não existe economicamente.
- Registro manual primeiro (Regra Zero): o valor está no casamento livro×cobertura, não na
  integração bancária, que depende de contrato/credenciais do cliente (checklist 19l).
- Testes provam a propriedade: hedge total silencia o alerta (base descoberta 11100→0.00);
  hedge de 50% reduz o limiar na proporção (5550→111) e mantém o alerta quando o drift ainda
  cruza (`ForwardContractIntegrationTest`, 5/5).

## Alternativas descartadas

- **Integração bancária (registro automático):** sem contrato/fluxo definidos; seam documentado.
- **Casamento de hedge por posição (1:1):** complexidade sem pedido; o abatimento proporcional
  por moeda cobre a necessidade do alerta.
- **IOF/spread como custo de liquidação já:** campo especulativo até o fluxo bancário real
  (seam da DL-0049 mantido, Open Question na SPEC-0032).
- **`HedgeAdvisor` agora:** exigiria estender o agregado `Insight` acoplado ao
  `PromoFxAssessment`; a Fase 20c o reconstrói sobre modelos reais — fazer duas vezes viola a
  Regra Zero.

## Impacto

- **Arquivos:** `ForwardContract`/`ForwardStatus`/`ForwardService`/`ForwardContractRepository`/
  `ForwardContractView` + 3 exceções (novos); `ExchangeExposureService` (cobertura por moeda);
  `LiveExposureView` (+2 campos); `ExchangeForwardController` + `RegisterForwardRequest`;
  `HttpErrorMapping` (+3); `ApiAuthorizationMatrix` (+1 regra); i18n (+3 chaves pt/en);
  FX desk (tela + service + models + i18n). Migração **V40**.
- **Testes:** `ForwardContractIntegrationTest` (5); exposição/matriz atualizadas; spec do
  frontend com fixture estendida.
- **Contratos:** `GET /api/exchange/exposure` ganha 2 campos (aditivo); 4 rotas novas de
  forwards; snapshot OpenAPI regenerado.

## Como reverter

Barata: remover as rotas/tela e voltar o limiar à exposição total (V40 fica órfã, aditiva).
Reverteria o alerta ao comportamento "com ruído" — não recomendado.
