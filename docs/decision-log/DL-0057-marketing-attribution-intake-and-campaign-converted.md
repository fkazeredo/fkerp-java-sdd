# DL-0057 — Marketing: atribuição por intake próprio (código→reserva) em vez de alterar `BookingConfirmed`; publica `CampaignConverted`

- **Fase:** 8f (Marketing — SPEC-0019)
- **Spec(s):** SPEC-0019 (BR5 "Attribution liga um código/UTM a uma reserva: ao receber
  `BookingConfirmed` com código de campanha, registra a atribuição; publica `CampaignConverted` para
  a Intelligence"; Events `CampaignConverted {campaignId, bookingId, occurredAt}`; API
  `GET /api/marketing/attribution?campaignId=`).
- **ADR relacionado:** modules-and-apis.md (sem FK cross-contexto; colaboração por fachada/evento;
  fronteiras preservam extração futura); messaging-and-integrations.md (idempotência por constraint);
  redesign Parte 8.2-F ("Atribuição de campanha → reserva (de Marketing)").
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A BR5 supõe que `BookingConfirmed` chegue "**com código de campanha**". Mas o evento atual
`BookingConfirmed {bookingId, quoteId, accountId, occurredAt}` **não carrega** código de campanha,
e o `booking`/`quoting` **não coletam** UTM/código hoje. Como ligar campanha→reserva sem inventar
dado no Booking nem criar acoplamento cíclico?

## Decisão

1. **Intake de atribuição próprio do Marketing** (não alterar o evento do Booking no v1): o
   `marketing` mantém um agregado `Attribution(campaign_code, booking_id, attributed_at)` com
   `UNIQUE (campaign_code, booking_id)` (idempotência, espelha a spec). A ligação é **registrada**
   por um endpoint próprio `POST /api/marketing/attribution` (corpo `{campaignCode, bookingId}`) —
   o portador do código (landing page/UTM/agente) informa o vínculo; é o ponto de entrada da
   atribuição que a spec coloca "de Marketing".
2. **Quando `BookingConfirmed` chega**, um listener interno do `marketing` verifica se **já existe**
   um `campaign_code` pré-registrado para aquele `bookingId` (via o intake acima) e, havendo,
   **confirma a conversão**: publica `CampaignConverted {campaignId, bookingId, occurredAt}` para a
   Intelligence (consumidor-folha, DL-0034). Sem código pré-registrado, **nada acontece** (não há
   atribuição forçada).
3. **Marketing depende só de eventos/tipos expostos** do Booking (`BookingConfirmed`) — **nunca** da
   fachada nem do `internal` do Booking. Booking **não** depende de Marketing. Grafo **acíclico**:
   `marketing → {booking (evento), money, error}`; `intelligence → marketing (evento
   CampaignConverted)`.
4. **`campaignCode` × `campaignId`:** o código é o token público de campanha (string em UTM); cada
   `Campaign` tem um `code` único. A `Attribution` guarda o **código** (estável, externo) e resolve
   o `campaignId` para o evento `CampaignConverted` (a Intelligence pensa em campanha).
5. **Gancho rastreável para o futuro:** quando/se o Booking passar a carregar `campaignCode` nativo
   (UTM capturado no Quoting), o listener do passo 2 troca o "consultar intake" por "ler o código do
   próprio evento" — mudança localizada, sem alterar a tabela `attributions`. Documentado como seam
   (simulation-and-mocking.md).

## Justificativa

- modules-and-apis.md proíbe FK cross-contexto e pede que **a fronteira preserve a extração futura**:
  o Marketing **não pode** forçar um campo no agregado do Booking só para sua conveniência. Manter a
  atribuição **dentro do Marketing** respeita a propriedade do dado (a atribuição é fato de
  Marketing, redesign 8.2-F) e mantém o Booking ignorante de campanhas.
- Alterar `BookingConfirmed` para carregar `campaignCode` seria uma mudança de **contrato de evento
  já consumido** por reconciliation/intelligence/finance — risco desproporcional (a spec o trata
  como contrato estável) por um dado que o Booking nem coleta. O intake próprio entrega o mesmo
  resultado de negócio (campanha→reserva→conversão) sem esse risco.
- A idempotência por `UNIQUE (campaign_code, booking_id)` é exatamente o que a spec pede e usa
  "constraint antes de infra complexa" (messaging-and-integrations.md).

## Alternativas descartadas

- **Adicionar `campaignCode` ao evento `BookingConfirmed` (e ao Booking/Quoting).** Descartada no v1:
  muda contrato de evento consumido por 3 módulos e exige coleta de UTM no fluxo de venda (escopo de
  outra spec). Fica como seam para quando a captura de UTM existir.
- **Marketing chamar `BookingService` para "carimbar" o código.** Descartada: viola a direção do
  grafo (criaria dependência Marketing→Booking de comando e tentação de FK), e o Booking não tem onde
  guardar.
- **Intelligence registrar a atribuição.** Descartada: a atribuição é fato de **Marketing** (redesign
  8.2-F); a Intelligence só **consome** `CampaignConverted` (advises-never-commands, SPEC-0013 BR2).

## Impacto

- **Specs:** SPEC-0019 BR5 concretizada como "ASSUMIDO (ver DL-0057): intake próprio + confirmação na
  `BookingConfirmed`".
- **Arquivos:** agregado `Attribution`, `AttributionRepository` (UNIQUE), `MarketingService.attribute`
  + listener `BookingEventsListener` (consome `BookingConfirmed`), evento `CampaignConverted`.
- **Migração:** `attributions` (V24) com `UNIQUE (campaign_code, booking_id)`.
- **Contratos:** `POST /api/marketing/attribution`; `GET /api/marketing/attribution?campaignId=`.
- **Modulith:** `marketing → booking` (só evento). **Acíclico.**

## Como reverter

Moderada: se o dono decidir capturar UTM no Booking, troca-se a fonte do código no listener (intake →
campo do evento) sem mexer na tabela `attributions` nem no contrato `CampaignConverted` — refator de
uma classe. O intake explícito pode coexistir (canais sem UTM).
