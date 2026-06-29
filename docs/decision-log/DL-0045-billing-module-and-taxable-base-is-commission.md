# DL-0045 — Billing: novo módulo `domain.billing`, agregado `CommissionInvoice` e base tributável = comissão

- **Fase:** 8c (Billing — SPEC-0016)
- **Spec(s):** SPEC-0016 (Goal; BR1 "base = comissão, nunca o pacote"; Scope; Persistence Changes)
- **ADR relacionado:** 0011, 0012, 0014 ; OVERVIEW 3.2/7.7 (linha 153: "imposto incide só sobre a comissão")
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0016 pede o agregado `CommissionInvoice`, mas faltava fixar: (a) que é um **novo módulo
Modulith** (`domain.billing`, o 13º) e não um apêndice do Finance; (b) **como** a nota referencia o
"lançamento de comissão (Finance)" sem FK cross-contexto; (c) a modelagem da **base tributável** que
materializa a tese econômica (imposto só sobre a comissão/spread, não sobre o valor que "passa").

## Decisão

1. **Novo módulo `com.fksoft.domain.billing`** com `@ApplicationModule(displayName = "Billing")`,
   pacote-base público + `internal` module-private (entidade/repositório), exatamente como os demais
   (Finance/Compliance/Reconciliation). Justificativa de fronteira (modules-and-apis.md): Billing tem
   **linguagem própria** (nota fiscal de serviço, ISS, retenções, número/código de verificação),
   **regras próprias** (cálculo tributário, idempotência por comissão, ciclo RASCUNHO→EMITIDA→
   CANCELADA) e **dono de dado próprio** (as notas). É Supporting, não genérico. Vira o **13º
   módulo** anotado.

2. **Referência ao lançamento de comissão por valor (`commissionEntryId : UUID`), nunca FK.**
   Billing **não** importa entidade/repositório do Finance — e, para manter o grafo de módulos
   **acíclico** (ver DL-0047: `finance → billing` via evento), o módulo `domain.billing` é **folha** e
   **não depende de `finance` de forma alguma**. A criação do rascunho recebe o `commissionEntryId`
   (id do lançamento de comissão a receber, `EntryType.COMMISSION_RECEIVABLE`) e a **base** (a
   comissão, `Money`) como **valores de entrada**. A validação de existência do lançamento, quando
   necessária, é feita no **orquestrador `infra`** (`BillingIssuanceService`, que pode ler o Finance
   pela porta `LedgerDirectory` — infra → domínio é permitido), nunca dentro do módulo de domínio
   Billing. **Decoplamento mantido:** nenhuma chamada ao repositório interno do Finance.

3. **Base tributável = a comissão (BR1), nunca o pacote.** O agregado guarda `base : Money` = o valor
   da comissão da Acme (o spread/receita). O cálculo de ISS/retenções (DL-0044) incide **só** sobre
   essa base. Isso é protegido por **teste de regressão explícito**: dado o exemplo do redesenho
   (tarifa USD 500 / pacote BRL 2.700; comissão BRL 405), o ISS = `issRate × 405`, e **um teste falha
   se algum dia a base virar o pacote (2.700)**. A nota **carrega apenas a base de comissão**; o valor
   do pacote nunca entra no agregado (não há campo para ele — Regra Zero).

4. **Ciclo de vida:** `RASCUNHO → EMITIDA → CANCELADA` (enum `InvoiceStatus`). Transições inválidas
   lançam `BillingInvoiceTransitionInvalidException`. Reemissão só após cancelamento (BR4/BR6 — ver
   DL-0047 para a idempotência por comissão).

5. **Idempotência estrutural por comissão (BR4):** `commission_entry_id` **UNIQUE** na tabela
   `commission_invoices` — não existem duas notas (não-canceladas) para a mesma comissão. (A
   idempotência de **emissão** e a interação com cancelamento estão no DL-0047.)

## Justificativa

- **OVERVIEW 3.2/7.7 + BR1** são inequívocos: "imposto incide só sobre a comissão; separar dinheiro
  que passa de receita". Modelar a base como a comissão (e **não ter** campo de pacote) é a forma mais
  honesta de impedir o erro.
- **Fronteira de módulo** (modules-and-apis.md): linguagem/regra/dado próprios justificam o módulo;
  não é "pasta organizada". O Finance é o razão; Billing é o documento fiscal — contextos distintos.
- **Referência por id + porta de leitura** segue o padrão DL-0041/Compliance (id de outro contexto é
  valor; colaboração só por fachada/porta). `billing → finance` (via `LedgerDirectory`) é **acíclico**:
  Finance não depende de Billing.

## Alternativas descartadas

- **Colocar a nota dentro do Finance.** Descartada: mistura razão contábil com documento fiscal;
  linguagem/ciclo/regra diferentes; incharia o Finance (genérico) com lógica fiscal (Supporting).
- **Guardar o valor do pacote no agregado "para auditoria".** Descartada por Regra Zero e por risco:
  campo que a spec não precisa e que **convidaria** a tributar o bruto. A proveniência do pacote vive
  no Quoting/Booking, não na nota.
- **FK `commission_entry_id → ledger_entries.id`.** Descartada: FK cross-contexto é proibida
  (modules-and-apis.md); a referência é valor + validação pela porta.

## Impacto

- **Specs:** SPEC-0016 — BR1 reforçada (base = comissão; sem campo de pacote).
- **Arquivos:** módulo `domain.billing` (`BillingService`, `CommissionInvoice` aggregate + `internal`
  repo, `InvoiceStatus`, views, eventos, exceções); migração **V20** (tabela `commission_invoices` com
  `commission_entry_id` UNIQUE).
- **Modulith:** 13º módulo anotado; `billing → finance` (porta), `billing → compliance` (porta de
  arquivamento, DL-0047), `billing → money`/`error` (kernels). Sem ciclo.

## Como reverter

Reversão **moderada e contida**: o módulo é novo e folha (ninguém depende de Billing). Dropar a
tabela `commission_invoices` (nova migração) e remover o pacote `domain.billing` + controller + i18n.
Finance/Compliance ficam intactos (Billing só os **consome** por porta). Nenhum contrato de outro
módulo muda.
