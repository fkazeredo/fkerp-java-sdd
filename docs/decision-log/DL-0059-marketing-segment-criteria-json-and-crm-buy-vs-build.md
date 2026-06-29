# DL-0059 — Marketing: critérios de Segment como JSON validado (sobre dados existentes) + fronteira "este módulo é camada de consentimento/atribuição, não CRM"

- **Fase:** 8f (Marketing — SPEC-0019)
- **Spec(s):** SPEC-0019 (BR3 "Segment definido por critérios sobre dados **já existentes**
  (Accounts/eventos), sem coletar dado novo — minimização"; Persistence `segments(... criteria_json
  jsonb ...)`; API `POST /api/marketing/segments` / `GET .../segments/{id}/preview`; Open Question:
  "Se a Acme precisa de **CRM completo**, avaliar **comprar** e usar este módulo como camada de
  consentimento/atribuição — decisão do dono"; Out of Scope: "CRM completo (comprar)").
- **ADR relacionado:** persistence.md (jsonb quando a forma é variável; índices), DL-0012/DL-0037
  (jsonb × colunas — escolher pela forma do dado), modules-and-apis.md (módulo é fronteira de
  negócio, não comprar sistema caseiro), redesign Parte 5 (Marketing = Supporting).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

(1) Como modelar os **critérios** do `Segment` (colunas fixas × jsonb) e como **validá-los** (BR3:
só sobre dados existentes); (2) **até onde** vai o módulo Marketing — a Open Question diz que CRM
completo é "comprar"; precisamos de uma fronteira clara para não construir um CRM caseiro (Rule Zero).

## Decisão

1. **`criteria_json` como jsonb validado** (acompanha a spec): o critério é uma estrutura **variável**
   por natureza (combinações de `accountType`, `volume`, `route`, etc.), então jsonb é o ajuste —
   colunas fixas engessariam. **Mas** o JSON é **validado no domínio** contra um **catálogo fechado de
   campos permitidos** (`SegmentCriteria` value object): apenas campos que mapeiam para **dados já
   existentes** (ex.: `accountType ∈ {AGENCY, AGENT}`) são aceitos; um campo desconhecido →
   `marketing.segment.invalid` (400). Isso cumpre a BR3 (minimização: nada de dado novo) e impede
   jsonb virar saco de gato.
2. **`GET /segments/{id}/preview` estima o alcance** consultando **somente dados já existentes** que
   o Marketing pode **ler** para projeção/relatório (modules-and-apis.md permite leitura de dado
   compartilhado para projeções; comandos respeitam o dono). No v1 o preview conta sobre os
   **titulares com consentimento** conhecidos pelo Marketing (a base de consentimento), com um seam
   rastreável para enriquecer via leitura de Accounts quando necessário — sem **coletar** nada novo.
3. **Fronteira explícita "não é CRM" (Out of Scope da spec, reafirmada):** o módulo `marketing`
   entrega **consentimento (LGPD) + segmentação simples + campanha/disparo via ACL + atribuição**.
   **NÃO** entrega: gestão de leads/funil, histórico de interações, lead scoring, automações
   multi-step, editor de conteúdo criativo. Se o dono exigir CRM pleno, a recomendação é **comprar** e
   plugar este módulo como **camada de consentimento/atribuição** do CRM externo (o `NewsletterSender`
   já é a porta de saída; um `ConsentQuery` poderia ser exposto ao CRM). Decisão registrada como a
   posição "comprar vs. construir" que o ROADMAP pede para módulos com essa natureza.
4. **`Campaign`** referencia o `Segment` por id (valor), tem `code` único (atribuição, DL-0057),
   `content_ref` (ponteiro para o criativo externo — **não** armazenamos o criativo), janela
   `window_from/to` e `status`. Disparo filtra por consentimento (BR2) e é idempotente por
   `(campaignId, recipient)` (BR4, `campaign_sends` UNIQUE).

## Justificativa

- jsonb com **catálogo fechado de campos** dá flexibilidade de combinação **sem** abrir mão da
  validação de domínio nem violar minimização (BR3) — equilíbrio que persistence.md endossa (jsonb
  quando a forma varia; validar na borda). É o mesmo critério usado para escolher jsonb × colunas no
  projeto (DL-0012/DL-0037).
- Fixar a fronteira "não é CRM" **agora** evita o anti-padrão que o próprio ROADMAP cita do fkerp-poc
  (construir demais e reverter) — Rule Zero. A spec já coloca CRM como "comprar"; a decisão só torna
  isso operacional (o que entra, o que fica de fora, como plugar um CRM externo).

## Alternativas descartadas

- **Critérios em colunas fixas.** Descartada: o conjunto de critérios é combinatório e evolutivo;
  colunas fixas exigiriam migração a cada novo critério (engessa).
- **jsonb livre sem validação.** Descartada: viraria saco de gato e poderia aceitar campos que
  **coletam dado novo** (viola BR3) ou que vazam PII; o catálogo fechado evita isso.
- **Construir CRM completo no módulo.** Descartada explicitamente: Out of Scope da spec + Rule Zero;
  a recomendação é comprar e usar este módulo como camada de consentimento/atribuição.
- **Preview varrendo todos os contextos (Accounts/Booking) com joins.** Descartada no v1: começa pela
  base de consentimento (dado que o módulo possui) + seam rastreável; evita acoplamento prematuro.

## Impacto

- **Specs:** SPEC-0019 BR3 e a Open Question de CRM concretizadas como "ASSUMIDO (ver DL-0059)".
- **Arquivos:** `Segment` (entidade, `criteria_json`), `SegmentCriteria` (value object validado +
  catálogo de campos), `Campaign` (entidade), `MarketingService.defineSegment/previewSegment/
  createCampaign`, exceção `SegmentInvalidException` (`marketing.segment.invalid`).
- **Migração:** `segments` (V24, `criteria_json jsonb`), `campaigns`, `campaign_sends` (UNIQUE).
- **Contratos:** `POST /api/marketing/segments`, `GET /segments/{id}/preview`,
  `POST /api/marketing/campaigns`.

## Como reverter

Moderada: trocar jsonb por colunas (ou vice-versa) é uma migração + ajuste do mapeamento do value
object. Mudar a fronteira "não é CRM" (decidir construir leads/funil) é **escopo novo** (nova spec) —
não um refactor; por isso a fronteira fica documentada para o dono decidir conscientemente.
