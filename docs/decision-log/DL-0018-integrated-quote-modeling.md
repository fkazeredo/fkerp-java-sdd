# DL-0018 — Modelagem do Quote INTEGRATED: reusar o agregado, colunas de composição nulas

- **Fase:** 3 (Primeira integração real — ACL)
- **Spec(s):** SPEC-0009 (BR2, "criar o Quote com `priceOrigin = INTEGRATED` e `trustExternalPrice =
  true`; o motor de sugestão NÃO roda; nenhum OverrideRecord"); SPEC-0005 (redesenho 7.6 / Parte 10 — o
  gancho adormecido)
- **ADR relacionado:** 0011, 0012
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0009 ativa o ramo **INTEGRATED** do `Quote` (SPEC-0005), mas o agregado `Quote` e a tabela
`quotes` nasceram **100% MANUAL**: `fx_rate`, `rate_id`, `base_converted_amount`, `supplier_pct`,
`agent_pct`, as 3 comissões, `spread`, `markup_*` são **NOT NULL**. Um Quote INTEGRATED **não tem**
câmbio, comissão nem markup (o preço externo é confiável e fechado — não se recompõe). Falta decidir
**como** representar o INTEGRATED sem deformar o agregado nem o MANUAL.

## Decisão

- **Reusar o agregado `Quote`** (não criar um agregado paralelo): a tese do redesenho é "manual e
  integrado geram os **mesmos eventos**; a diferença é **atributo, não fluxo**" (OVERVIEW Parte 4.5).
- Novo factory **`Quote.composeIntegrated(accountId, sourceOfferId, externalPrice, validUntil, now,
  actor)`**:
  - `priceOrigin = INTEGRATED`; `suggestedAmount == appliedAmount == externalPrice` (BR2);
  - **não roda** o motor de sugestão; **nenhum OverrideRecord**;
  - campos de composição MANUAL (`fxRate`, `rateId`, `baseConverted`, `supplierPct`, `agentPct`,
    comissões, `spread`, `markup*`) ficam **nulos**;
  - `sourceOfferId` (novo campo) liga o Quote à `SourcedOffer` (rastro de procedência).
- **Migração `V10__quotes_integrated_and_source_offer.sql`** (aditiva): `ADD COLUMN source_offer_id
  uuid null` e **afrouxa para NULL** as colunas de composição MANUAL (`ALTER COLUMN … DROP NOT NULL`).
  Quotes MANUAL existentes continuam preenchendo tudo — a invariante "MANUAL congela proveniência"
  passa a ser garantida **no domínio** (factory `compose`), não mais por `NOT NULL` no schema.
- `QuoteView` para INTEGRATED: `commission`/`markup`/`fxRate`/`baseConverted`/`provenance.rateId`
  **nulos**; `priceOrigin = INTEGRATED`. Override **não se aplica** a INTEGRATED
  (`applyOverride` lança `quoting.override.not-applicable` / 409 se chamado — não há divergência contra
  sugestão porque o preço é confiável, BR2).

## Justificativa

- Reusar o agregado honra a tese "mesmo fluxo, atributo diferente" e evita duplicar `quotes`/`bookings`
  /`reconciliation` por origem (Regra Zero).
- Tornar as colunas MANUAL **nulas** é a mudança aditiva mínima (a spec já previa `source_offer_id`
  aditivo). A integridade do MANUAL migra para o domínio, onde já vive (factory `compose` preenche tudo;
  ArchUnit/Modulith e os testes de regressão da 0005 continuam verdes).
- O motor de sugestão **só roda no MANUAL** (redesenho Parte 4.3); INTEGRATED confia no preço externo —
  bloquear override em INTEGRATED previne "inventar" divergência contra uma sugestão que não existe.

## Alternativas descartadas

- **Agregado/tabela separados para INTEGRATED.** Descartada: duplica ciclo (Booking/Reconciliation
  leem `QuoteDirectory`), contraria "mesmo fluxo" e infla o schema.
- **Preencher os campos MANUAL com zero no INTEGRATED.** Descartada: zero é **mentira de
  proveniência** (sugere comissão/câmbio calculados); nulo diz honestamente "não houve composição".
- **Coluna discriminadora + herança JPA.** Descartada: complexidade sem ganho; um único `priceOrigin`
  já discrimina e o `internal` esconde o mapeamento.

## Impacto

- `domain.quoting`: `Quote.composeIntegrated(...)`; `applyOverride` recusa INTEGRATED
  (`QuoteOverrideNotApplicableException` → 409); `QuoteService.composeIntegrated(...)` (chamado pela
  ACL); `toView`/`toSnapshot` tolerantes a nulos; `QuoteComposed` carrega `priceOrigin=INTEGRATED`.
- Migração `V10__quotes_integrated_and_source_offer.sql`.
- `HttpErrorMapping` + i18n: `quoting.override.not-applicable`.
- Regressão SPEC-0005: a imutabilidade da proveniência MANUAL segue coberta pelos testes existentes.

## Como reverter

Se o negócio decidir **recompor** o preço integrado (sair do "adormecido", redesenho Parte 4.3),
adiciona-se um caminho de composição no INTEGRATED preenchendo os campos hoje nulos — sem quebrar o
schema (as colunas já existem). Reversão moderada e aditiva.
