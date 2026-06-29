# DL-0055 — Marketing: porta `NewsletterSender` (ACL) + mock rastreável; modelo de consentimento = single opt-in

- **Fase:** 8f (Marketing — SPEC-0019)
- **Spec(s):** SPEC-0019 (Scope: disparo via **porta de newsletter** ACL; BR1 consentimento como
  objeto; BR4 idempotência por `(campaignId, recipient)`; Open Questions: "Provedor de newsletter e
  modelo de consentimento (double opt-in?) — em aberto (define a ACL)").
- **ADR relacionado:** 0007 (estratégia de provedor de e-mail — porta `EmailSender` + adaptador),
  0010 (camada infra centralizada), 0006 (mock rastreável); messaging-and-integrations.md
  (notificações = integração externa, abstração + i18n); modules-and-apis.md (porta no módulo,
  adaptador em `infra`).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0019 deixa **em aberto** (1) qual **provedor de newsletter** (Mailchimp/RD/SES…) e (2) o
**modelo de consentimento** — *single opt-in* (consentimento registrado já é válido) × *double
opt-in* (exige confirmação por e-mail antes de valer). Ambos afetam a ACL e o fluxo de envio.

## Decisão

1. **Porta de domínio `NewsletterSender`** no módulo `marketing` (não no domínio de outro módulo),
   espelhando ADR 0007 (`EmailSender`): o domínio fala em tipos próprios (`NewsletterMessage` com
   `campaignId`, `recipientRef`, `contentRef`) e o adaptador concreto vive em
   `com.fksoft.infra.integration.newsletter`. O **DTO do provedor nunca cruza** para o domínio
   (regra ArchUnit nova, como nas ACLs de quotationsite/nfse/payment).
2. **Adaptador entregue = mock rastreável** (`SimulatedNewsletterSender`): registra o "envio"
   (log de evento de negócio, sem PII), devolve um `providerMessageRef`, e classifica falha
   (TIMEOUT/UNAVAILABLE/REJECTED) como as demais ACLs. Um provedor real é **um novo adaptador**, sem
   mudança de domínio.
3. **Modelo de consentimento = single opt-in no v1**, com o **modelo de dados pronto para double
   opt-in** sem refator: o agregado `Consent` já guarda `status ∈ {GRANTED, REVOKED}`, `source`,
   `legalBasis` e `timestamp` (BR1). Double opt-in seria um estado intermediário `PENDING` +
   um passo de confirmação — adiável como evolução aditiva (novo valor de enum + endpoint de
   confirmação), **não** uma reescrita.
4. **O consentimento é consultado ANTES de enfileirar** (BR2): o filtro roda no `marketing`, e só
   `recipientRef` com `Consent GRANTED` para aquela `purpose` entram no disparo; os demais são
   **suprimidos e contados** (não é erro global).

## Justificativa

- ADR 0007 já fixou "provedor de e-mail atrás de porta + estratégia trocável"; newsletter é o mesmo
  padrão de notificação externa (messaging-and-integrations.md) — reusar evita inventar arquitetura.
- **Single opt-in** é o **default defensável**: a LGPD (Lei 13.709/2018) exige consentimento
  **informado, específico e destacado**, mas **não** exige tecnicamente o duplo opt-in (que é
  *boa prática de entregabilidade*, não requisito legal). Para B2B (agências/agentes, não pessoa
  física consumidora), o registro verificável do consentimento + respeito à revogação já satisfaz a
  base legal; o duplo opt-in pode entrar quando o provedor real for escolhido (ele costuma oferecer
  a confirmação nativamente). Mirar single opt-in agora **não cria dívida** porque o modelo já
  comporta o estado `PENDING`.
- Mock rastreável (ADR 0006) prova o contrato da porta sem acoplar a um SaaS antes de o dono
  escolher — exatamente o que a Open Question pede ("define a ACL").

## Alternativas descartadas

- **Double opt-in no v1.** Descartada: adiciona um fluxo de confirmação (token, e-mail de
  confirmação, expiração) que depende do provedor real ainda não escolhido — escopo e custo sem
  necessidade atual (Rule Zero). O modelo já permite plugá-lo depois.
- **Escolher um provedor concreto agora (ex.: Mailchimp).** Descartada: é decisão do dono (Open
  Question); o mock rastreável + porta deixam a escolha para quando houver contrato/credencial.
- **Enviar direto do controller / sem ACL.** Descartada: viola messaging-and-integrations.md
  (notificação é integração externa, exige abstração, timeout, classificação de falha).

## Impacto

- **Specs:** SPEC-0019 — Open Question "provedor + modelo de consentimento" → Business Rules
  "ASSUMIDO (ver DL-0055): porta `NewsletterSender`, mock rastreável, single opt-in".
- **Arquivos:** porta `marketing.NewsletterSender` + value `NewsletterMessage`/`NewsletterSendResult`;
  adaptador `infra.integration.newsletter.SimulatedNewsletterSender`; regra ArchUnit "DTO da
  newsletter não cruza para o domínio".
- **Migração:** `consents` (V24) com `status`/`source`/`legal_basis`/`purpose`/`created_at`.
- **Contratos:** `POST /api/marketing/campaigns/{id}/send` (filtra por consentimento, devolve
  `targeted/suppressedNoConsent/queued`).

## Como reverter

Moderada: trocar o mock por um provedor real é **um novo adaptador** implementando
`NewsletterSender` (sem tocar o domínio). Migrar para double opt-in é aditivo: novo valor de status
`PENDING` + endpoint de confirmação + ajuste do filtro de envio para exigir `GRANTED` (já é o caso).
Nenhum dado existente precisa de backfill destrutivo.
