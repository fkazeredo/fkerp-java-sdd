# DL-0009 — Quoting: fórmula de preço e moeda da base comissionável

- **Fase:** 1 (Núcleo comercial manual)
- **Spec(s):** SPEC-0005 (keystone); reflete em SPEC-0004 e SPEC-0007
- **ADR relacionado:** 0011, 0012, 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Cara

## Lacuna

A SPEC-0005 deixa em aberto a **fórmula exata do preço de venda** e a **moeda da base
comissionável**: o preço é `baseBRL + markup` ou a agência paga a tarifa e a margem é só o spread
(sem markup)? A base comissionável é a base **convertida (BRL)** ou a base na **moeda do fornecedor
(USD)**?

## Decisão

Adotar a **recomendação do arquiteto** (`docs/ROADMAP.md` → "Recomendações para as Open Questions"):

1. **Base comissionável em BRL**: `baseBRL = basePrice × taxaCongelada` (scale 2, HALF_UP). As três
   comissões (fornecedor/agente/spread) são calculadas **sobre o baseBRL**.
2. **Preço de venda = `baseBRL + markup`**, com `markup` **governado, default 0** (source
   `SYSTEM_DEFAULT`, via `MarkupProvider` stub de CommercialPolicy). A **margem primária é o spread**.
3. `suggestedAmount = baseBRL + markupAmount`; `appliedAmount = suggestedAmount` até haver override.

## Justificativa

- A venda à agência é **em BRL** — usar BRL na base evita misturar moeda na decomposição financeira.
- Bate **exatamente** com o exemplo da spec: USD 500 × 5,40 = **2.700 BRL** → 15%/10% →
  **405 / 270 / 135**; markup default 0 → `suggestedAmount = 2.700`.
- `markup` como add-on opcional cobre os dois mundos: **GSA/spread puro** (markup=0) e **tarifa +
  markup** (markup>0), sem refatorar quando CommercialPolicy (SPEC-0014) graduar o stub.
- Recomendação explícita do ROADMAP → adotada em modo autônomo (`RUN-PHASE.md`).

## Alternativas descartadas

- **Base comissionável em USD (moeda do fornecedor)** depois convertida. Descartado: produziria
  comissões em USD e exigiria converter cada comissão; mistura moeda e não bate com o exemplo (que
  mostra 405/270/135 em BRL).
- **Margem = só spread, sem markup.** Descartado como **única** opção: é um caso particular do
  modelo escolhido (markup=0); fixá-lo impediria tarifa+markup sem refatorar.

## Impacto

- `quoting`: composição congela `baseBRL`, as 3 comissões, markup e `suggestedAmount` (BR4).
- `commissioning`: recebe a base **já em BRL**.
- `reconciliation` (SPEC-0007): `expectedSpread` herda essa decomposição congelada.
- Migração `V4__create_quotes.sql` materializa a proveniência.

## Como reverter

Mudar a fórmula afeta composição, proveniência congelada e a conciliação esperada — **refactoring
caro** (toca quoting + reconciliation + dados já compostos). Por isso Reversibilidade=Cara: confirmar
com o dono antes de escalar volume.

## Revisão — Fase 19b (2026-07-02)

**MANTIDA.** Confrontada com o mercado (rules-engines de markup de Tourplan/Lemax; modelo GSA de
spread), a fórmula `baseBRL + markup governado (default 0)` cobre os dois mundos sem refator e
bate com o exemplo canônico. Continua valendo a ressalva original: confirmar com o dono antes de
escalar volume (Reversibilidade=Cara).
