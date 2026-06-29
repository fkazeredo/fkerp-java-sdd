# DL-0050 — Payout: parcelamento sem juros (v1) + distribuição exata de centavos

- **Fase:** 8d (Payout — SPEC-0017)
- **Spec(s):** SPEC-0017 (Open Question "Política de parcelamento (quem pode, juros) — depende de regra de
  negócio (CommercialPolicy)"; BR6 "um Payout pode ter N parcelas … cada parcela executa e comprova
  individualmente; o Payout só fica EXECUTED quando todas executam"; Tests Required "parcelamento …
  Payout só EXECUTED quando todas as parcelas executam")
- **ADR relacionado:** `architecture/backend.md` (money, HALF_UP, sem vazamento de centavos); Rule Zero
  (CLAUDE.md — não antecipar juros que o negócio ainda não deu)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC deixa em aberto a **política de parcelamento** (quem pode parcelar, se há juros) — depende de
CommercialPolicy (SPEC-0014). Mas BR6 e os Tests Required **exigem** o mecanismo: N parcelas com
vencimentos, cada uma executa/comprova, e o Payout só fica `EXECUTED` quando **todas** executam. Era
preciso decidir como gerar as parcelas sem inventar a política comercial.

## Decisão

1. **v1 sem juros:** a soma das parcelas == o `amount` total, **exata** (nenhum juro adicionado). Juros e
   elegibilidade (quem pode) ficam para uma fatia futura governada por CommercialPolicy — **não**
   antecipados (Rule Zero).
2. **Distribuição de centavos exata:** ao dividir o total em N parcelas, divide-se em escala 2 (HALF_UP)
   e o **resto de centavos** (total − Σ parcelas iguais) é somado à **primeira** parcela, de modo que
   `Σ parcelas == total` **ao centavo**. Ex.: R$ 100,00 / 3 = 33,34 + 33,33 + 33,33 (a 1ª absorve o
   centavo). Provado por teste unitário de domínio (cent distribution).
3. **Plano explícito também aceito:** o cliente pode informar as parcelas (valor + vencimento) em vez de
   só o número; nesse caso valida-se que **Σ parcelas == total** (senão erro de validação) — sem inventar
   rateio.
4. **Estado:** cada parcela tem status PENDING→EXECUTING→EXECUTED|FAILED; o **Payout** fica `EXECUTED`
   **somente** quando todas as parcelas estão `EXECUTED` (BR6); uma parcela `FAILED` deixa o Payout em
   `FAILED` (sem "pago" falso). Um payout sem plano de parcelas é tratado como **uma** parcela implícita
   (seq 1 = total) — caminho único de execução, sem ramo especial.

## Justificativa

- BR6/Tests Required pedem o mecanismo, não a política — então entrega-se o **mecanismo** (parcelas que
  executam individualmente, Payout EXECUTED só com todas) e **adia-se** a política (juros/elegibilidade),
  marcando a Open Question como ASSUMIDO com o default mais defensável (sem juros).
- A distribuição de centavos com resto na 1ª parcela é o jeito padrão de não vazar arredondamento
  (`backend.md`: money HALF_UP, somar tem de bater exato). Testar a soma é requisito explícito do
  supervisor.
- Tratar "sem plano" como uma parcela implícita evita duplicar o caminho de execução (Rule Zero — menos
  complexidade).

## Alternativas descartadas

- **Adicionar juros agora.** Descartada: política de negócio em aberto (CommercialPolicy); inventá-la
  seria criar regra. Default sem juros é o mais defensável.
- **Espalhar o resto de centavos na última parcela.** Funciona igual; escolheu-se a **primeira** por
  convenção determinística e testável (qualquer das duas seria válida — a invariante é Σ == total).
- **Rejeitar payout sem plano de parcelas.** Descartada: o caso comum é à vista; tratá-lo como 1 parcela
  unifica o fluxo.

## Impacto

- **Specs:** SPEC-0017 Open Question "parcelamento" → **ASSUMIDO (ver DL-0050)**.
- **Arquivos:** `payout_installments` (V21); geração/validação do plano em `Payout`/`PayoutService`;
  agregação de status (Payout EXECUTED quando todas).
- **Contratos:** `POST /api/payouts` aceita `installmentCount` **ou** `installments[{dueDate,amount}]`
  opcional.

## Como reverter

Reversão **moderada**: ligar juros = adicionar uma estratégia de cálculo de plano (a partir de
CommercialPolicy) que produz parcelas com `amount` já com juros — o resto do fluxo (execução por parcela,
agregação de status) **não muda**. Mudar a regra de distribuição de centavos é trocar uma linha + o teste.
