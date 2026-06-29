# DL-0021 — Merchant of record é atributo por marca/contrato; default afiliado (costBearer=SUPPLIER)

- **Fase:** 4 (Cancelamento como objeto + armadilha do merchant)
- **Spec(s):** SPEC-0010 (Open Question **Q3**; BR3; BR5; "armadilha do `ALL_SALES_FINAL`")
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta (adota a Recomendação explícita do arquiteto no ROADMAP)
- **Reversibilidade:** Moderada

## Lacuna

Q3: se o portal é **merchant of record**, ele **assume** a obrigação com a marketplace e o reembolso
ao cliente; se **afiliado**, não. Isso define **quem** é o `costBearer` no `ALL_SALES_FINAL`. A spec
diz que "o modelo suporta ambos, mas o default precisa ser confirmado".

## Decisão

- Modelar **`merchantOfRecord` como atributo** da `CancellationPolicy` (por marca/contrato — é
  **dado**, não flag global). Default **`false` (afiliado)**.
- Resolução do `costBearer` do `ALL_SALES_FINAL` (BR3/BR5):
  - **afiliado** (`merchantOfRecord=false`): o custo ao fornecedor é devido pelo **SUPPLIER** (o
    arranjo afiliado não transfere a cobrança para a Acme); não há `CUSTOMER_REFUND` assumido pela
    Acme por padrão.
  - **merchant** (`merchantOfRecord=true`, caso **Portal de Experiências**): a **Acme** assume —
    `SupplierCharge.costBearer = ACME` (devido à marketplace mesmo cancelando) **e**, havendo
    reembolso comercial ao cliente, `CustomerRefund.costBearer = ACME`. São **duas obrigações
    distintas que não se anulam** (a armadilha — BR5).
- Para STANDARD/CUSTOM, o `costBearer` da multa vem do campo `costBearer` da própria política
  (∈ {AGENCY, ACME, SUPPLIER}).

## Justificativa

- **Recomendação do ROADMAP (Q3), adotada na íntegra:** "Atributo por marca/contrato
  (`merchantOfRecord`); default afiliado (costBearer=fornecedor); merchant=true no Portal de
  Experiências (costBearer=Acme). Arranjos variam por marca → é **dado**, não flag global."
- O caso canônico do redesenho (OVERVIEW 7.4 / 8.2-G): o Portal de Experiências é cobrado pela
  Marketplace de Tours **mesmo reembolsando o cliente** — isto **é** o merchant daquele acordo
  específico, e o modelo o expressa sem hardcode.
- Default afiliado é o mais conservador: a Acme **não** assume custo/reembolso a menos que o contrato
  (dado) diga que é merchant.

## Alternativas descartadas

- **Flag global merchant/afiliado.** Descartada: arranjos variam por marca; global forçaria todas as
  marcas ao mesmo papel (contraria a Recomendação e o redesenho).
- **Default merchant=true.** Descartada: faria a Acme assumir obrigações por omissão — risco
  financeiro invisível, o oposto do que a fase quer **tornar visível**.
- **Derivar merchant do tipo da política.** Descartada: `ALL_SALES_FINAL` e merchant são ortogonais
  (pode haver venda merchant com política STANDARD); manter separados evita acoplar conceitos.

## Impacto

- `CancellationPolicy` ganha `merchantOfRecord` (boolean) e a regra de resolução do `costBearer` do
  ALL_SALES_FINAL.
- `cancellation_policies` e `booking_cancellation_snapshots` ganham coluna `merchant_of_record`.
- A armadilha do merchant (DL-0024) materializa `SUPPLIER` + `CUSTOMER_REFUND` com `costBearer=ACME`.

## Como reverter

Se o dono confirmar outro default ou outra atribuição de `costBearer`, muda-se a regra de resolução
(um método de domínio) e o default da coluna — **sem** mudar o schema (a coluna já existe). Reversão
moderada e localizada.
