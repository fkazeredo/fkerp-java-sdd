# Caderno de testes — Índice

Um arquivo por fatia, com escopo, casos por tipo (unitário/arquitetura/integração/e2e/smoke),
resultado, cobertura e como reproduzir.

| Fatia | Spec | Arquivo | Resultado |
|---|---|---|---|
| Slice 0 — Walking Skeleton | SPEC-0001 | [slice-0-walking-skeleton.md](slice-0-walking-skeleton.md) | ✅ verde (backend 12 testes, frontend 4 testes, smoke OK) |
| Slice 1 — Accounts | SPEC-0002 | [slice-1-accounts.md](slice-1-accounts.md) | ✅ verde (backend 32 testes; tela Angular pendente) |
| Slice 2 — Exchange | SPEC-0003 | [slice-2-exchange.md](slice-2-exchange.md) | ✅ verde (backend 46 testes; tela Angular pendente) |
| Slice 3 — Commissioning | SPEC-0004 | [slice-3-commissioning.md](slice-3-commissioning.md) | ✅ verde (backend 54 testes; tela Angular pendente) |
| Slice 4 — Quoting (keystone) | SPEC-0005 | [slice-4-quoting.md](slice-4-quoting.md) | ✅ verde (backend 62 testes; tela Angular pendente) |
| Slice 5 — Booking | SPEC-0006 | [slice-5-booking.md](slice-5-booking.md) | ✅ verde (backend 73 testes; tela Angular pendente) |
| Slice 6 — Reconciliation | SPEC-0007 | [slice-6-reconciliation.md](slice-6-reconciliation.md) | ✅ verde (backend 82 testes) |
| Fase 1 — Telas Angular | SPEC-0002…0007 | [release-notes/0.2.1.md](../release-notes/0.2.1.md) | ✅ verde (frontend: lint + 14 testes + build; 5 telas + nav) |
| Slice 7a — Finance | SPEC-0015 | [slice-7a-finance.md](slice-7a-finance.md) | ✅ verde (backend 95 testes; veto real na 7c) |
| Slice 7b — Compliance | SPEC-0008 | [slice-7b-compliance.md](slice-7b-compliance.md) | ✅ verde (backend 106 testes; cofre + close-check + retenção) |
| Slice 7c — Veto de fechamento | SPEC-0008/0015 | [slice-7c-close-veto.md](slice-7c-close-veto.md) | ✅ verde (backend 108 testes; regra de ouro + job de retenção) |
| Slice 8a — Sourcing | SPEC-0009 | [slice-8a-sourcing.md](slice-8a-sourcing.md) | ✅ verde (backend 114 testes; SourcedOffer + offers API) |
| Slice 8b — Quoting ramo INTEGRATED | SPEC-0009/0005 | [slice-8b-integrated-quote.md](slice-8b-integrated-quote.md) | ✅ verde (backend 118 testes; composeIntegrated + porta + V10) |
| Slice 8c — ACL de entrada (webhook) | SPEC-0009 | [slice-8c-inbound-acl.md](slice-8c-inbound-acl.md) | ✅ verde (backend 135 testes; HMAC + tradução ACL + idempotência + INTEGRATED ponta a ponta) |
| Slice 9a — CancellationPolicy como objeto | SPEC-0010 | [slice-9a-cancellation-policy-object.md](slice-9a-cancellation-policy-object.md) | ✅ verde (backend 145 testes; política como objeto + fonte administrável V12) |
| Slice 9b — Cancelamento rico + armadilha do merchant | SPEC-0010 | [slice-9b-cancel-charges-merchant-trap.md](slice-9b-cancel-charges-merchant-trap.md) | ✅ verde (backend 151 testes; congelamento + 2 obrigações que não se anulam, V13) |
| Slice 9c — NoShowPolicy | SPEC-0010 | [slice-9c-no-show-policy.md](slice-9c-no-show-policy.md) | ✅ verde (backend 157 testes; fee + dispensa por prova de voo) |
| Slice 10a — Market Rate | SPEC-0011 | [slice-10a-market-rate.md](slice-10a-market-rate.md) | ✅ verde (backend 162 testes; taxa de mercado + porta + V14) |
| Slice 10b — FxPosition (subsídio × drift) | SPEC-0011 | [slice-10b-fx-position.md](slice-10b-fx-position.md) | ✅ verde (backend 174 testes; subsídio/drift/gap + V15; exemplo 7.2 provado) |
| Slice 10c — Relatórios de câmbio (LiveExposure/PromoFx) | SPEC-0011 | [slice-10c-fx-reports.md](slice-10c-fx-reports.md) | ✅ verde (backend 179 testes; agregado do livro + alerta 2% + promo-fx) |
| Slice 11a — People + snapshot operacional | SPEC-0012 | [slice-11a-people-snapshot.md](slice-11a-people-snapshot.md) | ✅ verde (backend 187 testes; módulo people 11º + idempotência + histórico + V16) |
| Slice 11b — Crawler ACL + fila + disjuntor | SPEC-0012 | [slice-11b-crawler-resilience.md](slice-11b-crawler-resilience.md) | ✅ verde (backend 197 testes; circuit breaker + retry/dead-letter + ACL + fronteira ArchUnit) |
| Slice 11c — Ingestão AFD/AEJ assinado → cofre | SPEC-0012 | [slice-11c-afd-legal-ingestion.md](slice-11c-afd-legal-ingestion.md) | ✅ verde (backend 206 testes; verificação CAdES/PKCS#7 + cofre com retenção 5 anos) |
| Slice 12a — Intelligence framework + PromoFxAdvisor | SPEC-0013 | [slice-12a-intelligence-promofx.md](slice-12a-intelligence-promofx.md) | ✅ verde (backend 216 testes; 12º módulo + insight read-model + advisor determinístico + V17 + ArchUnit "aconselha, nunca comanda" com dentes) |
| Slice 12b — OverrideNudge (gated) + decisão + observabilidade | SPEC-0013 | [slice-12b-nudge-decision.md](slice-12b-nudge-decision.md) | ✅ verde (backend 219 testes; nudge off por flag sem dado falso + decisão humana sem ação + InsightDecided) |
| Slice 8a — CommercialPolicy (parâmetros governados + precedência) | SPEC-0014 | [slice-8a-commercial-policy.md](slice-8a-commercial-policy.md) | ✅ verde (backend 239 testes; motor de precedência Diretiva>Promoção>Contrato>Política>Padrão com proveniência + V18 seed; gradua o stub de markup sem quebrar o Quoting; diretiva auditada vence imediatamente) |
| Slice 8b-1 — Finance: lançamento automático de AP/AR por evento | SPEC-0015 (full) | [slice-8b1-finance-event-posting.md](slice-8b1-finance-event-posting.md) | ✅ verde (backend 243 testes; consome eventos do Booking → AP/AR idempotente UNIQUE(source_ref,charge_kind) + V19; merchant trap preservado; regra de ouro intacta; Modulith acíclico finance→booking) |
| Slice 8b-2 — Finance: balancete do período por moeda/status | SPEC-0015 (full) | [slice-8b2-finance-trial-balance.md](slice-8b2-finance-trial-balance.md) | ✅ verde (backend 245 testes; `GET /periods/{yyyymm}/trial-balance` por moeda com net=AR−AP + contagens por status; aditivo, sem migração; sem plano de contas — DL-0043) |
| Slice 8c-1 — Billing: cálculo de tributos + agregado | SPEC-0016 | [slice-8c1-billing-tax.md](slice-8c1-billing-tax.md) | ✅ verde (backend 255 testes; 13º módulo `billing` folha; ISS=alíquota×comissão HALF_UP + estratégia de regime trocável (Simples default, DL-0044); base=comissão nunca o pacote (BR1); V20 commission_invoices UNIQUE parcial + municipal_iss_rates seed) |
| Slice 8c-2 — Billing: emissão NFS-e (ACL + arquivamento + lançamento) | SPEC-0016 | [slice-8c2-nfse-issuance.md](slice-8c2-nfse-issuance.md) | ✅ verde (backend 261 testes; porta `NfseGateway` + mock rastreável `infra.integration.nfse` (vendor não vaza, ArchUnit); `CertificateSigner` stub→SPEC-0023; orquestrador infra arquiva no Compliance + Finance lança ISS por evento `CommissionInvoiceIssued` (TAX_PAYABLE, idempotente, finance→billing acíclico); BR7 422/502; regressão DocumentRequirement falha-antes/passa-depois) |
| Slice 8c-3 — Billing: API REST + cancelamento | SPEC-0016 | [slice-8c3-billing-api.md](slice-8c3-billing-api.md) | ✅ verde (backend 265 testes; `BillingController` create/issue/cancel/get; jornada 201→200→200→200 + sad paths 404/409/422; OpenAPI 0.11.0; HttpErrorMapping completo) |
| Slice 8d-1 — Payout: agregado + parcelamento + API | SPEC-0017 | [slice-8d1-payout-aggregate.md](slice-8d1-payout-aggregate.md) | ✅ verde (backend 281 testes; 14º módulo `payout` folha; settlementRate→settledBrl (USD500×5,70=R$2.850); parcelamento com centavos exatos (resto na 1ª, DL-0050); REFUND exige origem (BR7); locking pessimista; V21) |
| Slice 8d-2 — Payout: ACL de pagamento + webhook assíncrono | SPEC-0017 | [slice-8d2-payment-acl-webhook.md](slice-8d2-payment-acl-webhook.md) | ✅ verde (backend 288 testes; porta `PaymentGateway` + mock rastreável webhook assíncrono HMAC (ADR 0006/DL-0048); request→PENDING; confirma/falha **idempotente** por (payoutId,seq,providerRef); falha→FAILED explícito; DTO do provedor não vaza (ArchUnit); V22) |
| Slice 8d-3 — Payout: SupplierSettled→Finance + comprovante + reembolso | SPEC-0017 | [slice-8d3-supplier-settled-finance-receipt-refund.md](slice-8d3-supplier-settled-finance-receipt-refund.md) | ✅ verde (backend 292 testes; `finance → payout` acíclico; liquidação posta ao Finance **uma vez** (R$2.850) + comprovante PAYMENT_PROOF/REFUND_PROOF no cofre; **reembolso NÃO cancela a obrigação do fornecedor** — armadilha do merchant verde, DL-0024/0051) |
| Slice 8e-1 — AfterSales: SupportCase + máquina de estado | SPEC-0018 | [8e-1-support-case-state-machine.md](8e-1-support-case-state-machine.md) | ✅ verde (backend 308 testes; 15º módulo `aftersales`; máquina OPEN→IN_PROGRESS→WAITING→RESOLVED→CLOSED (válidas+inválidas) + reabertura++; SLA derivado da CommercialPolicy no `open` (24h/72h/48h, seed V23, DL-0052); Modulith acíclico) |
| Slice 8e-2 — AfterSales: SLA breach (relógio controlado) + override por política | SPEC-0018 | [8e-2-sla-breach-controlled-clock.md](8e-2-sla-breach-controlled-clock.md) | ✅ verde (backend 317 testes; `markBreaches(now)` relógio controlado — dentro×fora p/ 1ª resposta/resolução/reembolso; breach é alerta que **não bloqueia** (status preservado) e idempotente; **Diretiva 72h→1h muda o SLA efetivo** provado; `SlaBreached` publicado) |
| Slice 8e-3 — AfterSales: resolução (Payout/Booking) + custo de servir + armadilha do merchant | SPEC-0018 | [8e-3-resolve-orchestration-merchant-trap.md](8e-3-resolve-orchestration-merchant-trap.md) | ✅ verde (backend 319 testes; `REFUND_APPROVED`→**1** Payout REFUND (origin=caseId) idempotente + custo de servir 492; reembolso **não cancela a obrigação do fornecedor** (armadilha do merchant verde, DL-0024/0051); `CANCEL_APPROVED`→`BookingService.cancel`→reserva CANCELLED (BR2); `aftersales→payout,booking,commercialpolicy` acíclico) |
| Slice 8f-1 — Marketing: Consent log append-only | SPEC-0019 | [8f-1-consent-log.md](8f-1-consent-log.md) | ✅ verde (backend 324 testes; 16º módulo `marketing`; consentimento LGPD cidadão de 1ª classe — conceder/revogar **apendam linha imutável**, estado = última linha por (titular, finalidade), histórico preservado, reconsent volta a GRANTED (BR1/DL-0056); V24 `consents` + índice; erros sem PII; Modulith acíclico) |

## Resumo por nível (Fase 0)

| Nível | Ferramenta | Resultado |
|---|---|---|
| Unitário / Arquitetura (back) | JUnit 5 + ArchUnit + Spring Modulith | ✅ 11 casos |
| Integração (back) | Testcontainers + Postgres | ✅ 1 caso |
| Unitário (front) | Vitest + jsdom | ✅ 4 casos |
| Smoke / E2E | docker-compose + curl | ✅ health 200 UP |
| Portões | Spotless, Checkstyle, ESLint | ✅ 0 violações |
