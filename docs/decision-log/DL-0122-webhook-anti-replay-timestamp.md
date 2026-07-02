# DL-0122 — Webhooks: assinatura com timestamp + janela anti-replay (helper único)

- **Fase:** 19c (Refactoring de maturidade — hardening)
- **Spec(s):** SPEC-0009 (BR3), SPEC-0017 (webhook do gateway)
- **ADR relacionado:** ADR 0006; `architecture/messaging-and-integrations.md`
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada (muda o contrato M2M dos 2 webhooks)

## Lacuna

Os dois webhooks (quotation-site inbound, gateway de pagamento) assinavam **só o corpo**
(`HMAC(body)`) com código **duplicado** em dois verificadores. Um corpo validamente assinado
podia ser **reenviado para sempre** (replay) — não há proteção temporal.

## Decisão

1. **Helper único** `WebhookSignatures` (`infra.integration`): HMAC-SHA256 de
   **`timestamp + "." + body`**, comparação em tempo constante, e **janela anti-replay** (o
   timestamp ISO-8601 deve estar dentro da tolerância; default **300s**). Remove a duplicação.
2. Header novo de timestamp: `X-Signature-Timestamp` (quotation-site) e
   `X-Payment-Signature-Timestamp` (pagamento). Ausente/malformado/fora da janela → **401**
   (mesmo código de assinatura inválida — não vaza o motivo exato; log `warn` distingue replay).
3. Os mocks rastreáveis e os testes assinam com `timestamp = agora`; a tolerância é configurável
   (`integration.*.replay-tolerance-seconds`).

## Justificativa

- Assinar o timestamp junto ao corpo é o padrão de webhooks (estilo Stripe/GitHub): sem ele, a
  assinatura não expira.
- Um helper único elimina o drift entre os dois verificadores (aderência de código — 19j).
- Manter o 401 genérico preserva o contrato de erro e não revela se foi replay ou assinatura.

## Alternativas descartadas

- **Nonce persistido por request:** mais forte, mas exige tabela + limpeza; a janela por timestamp
  cobre o risco real (replay de captura) com custo zero de estado. Seam para nonce fica aberto.
- **Manter só HMAC(body):** não expira a assinatura — o risco que a fatia existe para fechar.

## Impacto

- **Arquivos:** novo `WebhookSignatures`; `QuotationSiteSignatureVerifier`/`PayoutWebhookSignature`
  reescritos sobre ele; adapter/receiver/controllers/mock passam o timestamp; config
  `replay-tolerance-seconds`. Testes atualizados (assinam com timestamp) + `WebhookSignaturesTest`.
- **Contratos:** os 2 webhooks passam a **exigir** o header de timestamp (**breaking M2M**, ambos
  os pares são nossos/emulados) — destacado no release.

## Como reverter

Moderada: voltar a assinar só o corpo (helper com tolerância infinita e sem timestamp) — mas
reabre o replay. Preferir manter.
