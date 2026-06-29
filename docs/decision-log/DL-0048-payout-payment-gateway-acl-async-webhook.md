# DL-0048 — Payout: meio de pagamento como porta `PaymentGateway` + mock rastreável com webhook assíncrono

- **Fase:** 8d (Payout — SPEC-0017)
- **Spec(s):** SPEC-0017 (Open Question "Meio de pagamento/gateway real (PIX/TED/boleto/provedor) —
  define a ACL; em aberto"; BR2 transição financeira sem "executado" falso; BR3 idempotência por
  payoutId; Scope "execução via porta de pagamento (ACL)"; Validation "idempotência da execução;
  resposta do gateway validada")
- **ADR relacionado:** **0006 (mock payment + webhook assíncrono — padrão estabelecido)**, 0011, 0012;
  `architecture/messaging-and-integrations.md` (ACL, idempotência, classificação de falha, timeout)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0017 deixa **em aberto** qual é o meio de pagamento/gateway real (PIX/TED/boleto/provedor). Sem
ele, era preciso decidir **como** modelar a saída ao mundo externo de forma que (a) a troca para o
provedor real seja **configuração, não refator**; (b) a execução exercite a semântica de produção
(confirmação **assíncrona**, idempotência, validação de resposta, classificação de falha); (c) o DTO do
provedor **não vaze** para o domínio (ACL).

## Decisão

**Modelar como ACL com webhook assíncrono, exatamente como o ADR 0006** (que já é o padrão do projeto
para pagamento):

1. **Porta de domínio `PaymentGateway`** no módulo `payout` (`com.fksoft.domain.payout`):
   `PaymentRequestResult request(PaymentInstruction)` devolve **imediatamente** um `providerRef` + status
   **PENDING**. A confirmação final (`SUCCEEDED`/`FAILED`) chega **depois**, por **webhook**.
2. **Adaptador `MockPaymentGateway`** em `com.fksoft.infra.integration.payment` (default em dev/test):
   persiste um `MockPayoutJob` e devolve `providerRef`+PENDING. Um job (`@Scheduled`, atraso
   configurável; em teste, disparo determinístico via método de "drive") faz o mock **POSTar** um
   webhook **assinado (HMAC-SHA256)** para `POST /api/webhooks/payouts/mock` com o desfecho
   (configurável por metadata — default `SUCCEEDED`; `outcome=FAILED` exercita o caminho de falha).
   O provedor real (futuro) é **outro adaptador** da mesma porta — sem mudança de domínio.
3. **Handler do webhook** valida a assinatura, traduz o DTO **externo** (que vive só em
   `infra.integration.payment`) e processa **idempotente** por `(payoutId, installmentSeq, providerRef)`
   (UNIQUE em `processed_payout_webhooks` + state-check da parcela): sucesso → parcela `EXECUTED`,
   arquiva comprovante, publica evento; falha → parcela/Payout `FAILED` (sem comprovante, sem evento de
   sucesso — **nunca** "executado" falso, BR2). Reentrega = no-op (BR3).
4. **DTO do provedor não cruza para o domínio** — regra ArchUnit nova (`domain` não depende de
   `..infra.integration.payment..`), com teste-com-dentes que planta a violação e falha.

## Justificativa

- O **ADR 0006 já decidiu** este padrão (mock síncrono foi explicitamente **rejeitado** lá porque
  produz um fluxo fundamentalmente diferente do real). Seguir o ADR é autoridade direta (CLAUDE.md:
  ADRs acima de docs/código).
- `messaging-and-integrations.md` exige ACL para integração externa, DTO do provedor fora do domínio,
  timeout, idempotência e **classificação de falha** — tudo atendido pela porta + adaptador + handler.
- Espelha as ACLs já provadas das Fases 3 (webhook de entrada, HMAC), 6 (crawler) e 8c (NFS-e): mesma
  forma, risco conhecido.

## Alternativas descartadas

- **Mock síncrono (devolve "pago" na hora).** Descartada pelo próprio ADR 0006: muda o fluxo e teria de
  ser reescrito quando o provedor real (assíncrono) entrar; perde a prova de idempotência de reentrega.
- **Sem abstração (mock embutido no serviço de execução).** Descartada: viola a ACL
  (`messaging-and-integrations.md`); o DTO do provedor vazaria.
- **Escolher um provedor concreto agora (ex.: PagSeguro/Stripe).** Descartada: é **decisão de negócio em
  aberto** (Confiança=Baixa); fixá-la sem o dono seria inventar regra. O mock prova o contrato; o real é
  só trocar o adaptador.

## Impacto

- **Specs:** SPEC-0017 Open Question "Meio de pagamento" → **ASSUMIDO (ver DL-0048)** em Business Rules.
- **Arquivos:** porta `PaymentGateway` (+ `PaymentInstruction`, `PaymentRequestResult`,
  `PaymentOutcome`, `PaymentFailureClass`) no domínio; `MockPaymentGateway`, `PayoutWebhookSignature`,
  DTO externo, `MockPayoutJob`, `MockPayoutJobDispatcher` em `infra.integration.payment`;
  `PayoutWebhookController` em `application.api`; `processed_payout_webhooks` e `mock_payout_jobs` na
  migração **V22**; regra ArchUnit nova; i18n `payout.*`.
- **Modulith:** `payout` é folha; a orquestração e o webhook vivem em `infra`/`application` (permitido).

## Como reverter

Reversão **moderada**: para um provedor real, **adicionar** um `XxxPaymentGateway` implementando a mesma
porta e apontar a configuração para ele — **sem** mudar domínio, máquina de status, eventos ou contrato
REST. Remover o mock é apagar o adaptador + o job + a config default. Raio contido em
`infra.integration.payment`.
