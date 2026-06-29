# Decision Log — Índice

Registro append-only das decisões tomadas em modo autônomo (uma por arquivo
`DL-NNNN-*.md`). Cada decisão foi registrada **antes** do código que depende dela,
conforme `docs/RUN-PHASE.md`.

## ⚠️ Atenção (Reversibilidade=Cara ou Confiança=Baixa)

| DL | Título | Confiança | Reversibilidade | Por que destacada |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | Manter pacote base `com.fksoft` | Alta | **Cara** | Renomear o pacote raiz após código nascer gera diff amplo |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | Quoting: preço = base BRL + markup (default 0) | Média | **Cara** | Fórmula de preço move a tese econômica; refator amplo se mudar |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | Inbound: Account inexistente **rejeita** (422) | **Baixa** | Moderada | Decisão de negócio em aberto na SPEC-0009; só o dono fecha |
| [DL-0024](DL-0024-charges-are-distinct-facts-never-netted.md) | Encargos são fatos distintos que **nunca** se compensam (armadilha do merchant) | Alta | **Cara** | Tese econômica da Fase 4; sair do "sem netting" exige nova spec |
| [DL-0029](DL-0029-rep-type-target-rep-p-export-upload.md) | Tipo de REP (Q6): mirar **REP-P** e modelar AFD como upload da exportação oficial | **Baixa** | **Cara** | **Q6 — qual REP o cliente usa é incógnita de negócio**; muda formato/captura do artefato legal |
| [DL-0033](DL-0033-crawl-period-and-multi-source.md) | Periodicidade do crawl (diário, período corrente) e lista de `sourceRef` configurável | **Baixa** | Barata | Periodicidade/filiais reais são decisão de RH ainda não dada (só configuração) |
| [DL-0044](DL-0044-billing-tax-regime-simples-and-swappable-strategy.md) | Billing: regime **Simples Nacional** (default) + estratégia trocável de ISS/retenções (Q7) | **Baixa** | **Cara** | **Q7 — regime tributário/quem emite é incógnita de negócio (só o contador fecha)**; move a tese tributária e o impacto fiscal de notas já emitidas é externo e caro |
| [DL-0048](DL-0048-payout-payment-gateway-acl-async-webhook.md) | Payout: gateway de pagamento como porta + **mock rastreável com webhook assíncrono** (ADR 0006) | **Baixa** | Moderada | Meio de pagamento real é Open Question da SPEC-0017; só o dono fecha (o mock prova o contrato) |
| [DL-0049](DL-0049-payout-foreign-settlement-rate-and-brl-baixa.md) | Payout: liquidação do fornecedor com `settlementRate` (USD) + baixa em **BRL** | **Baixa** | **Cara** | Fluxo de câmbio real (remessa vs BRL) é Open Question; a tese de câmbio é compartilhada por Payout/Reconciliation/Exchange |

> _Nota Fase 8e:_ DL-0052/0053/0054 são **Confiança=Média / Reversibilidade=Barata–Moderada** —
> não entram neste destaque. O "quais custos contam" do custo de servir (DL-0053) e os prazos de
> SLA (DL-0052) seguem confirmáveis com o dono, mas a reversão é barata (parâmetro governado em
> runtime / value object local).

> DL-0017 (Fase 3), **DL-0029/DL-0033** (Fase 6), **DL-0044** (Fase 8c) e **DL-0048/DL-0049** (Fase 8d)
> são as de **Confiança=Baixa** (Open Questions de negócio em aberto). **DL-0029 (Q6), DL-0044 (Q7) e
> DL-0049** são as mais sensíveis: Confiança=Baixa **e** Reversibilidade=Cara — incógnitas de negócio que
> só o cliente/contador fecha (tipo de REP; regime tributário; fluxo de câmbio da liquidação).
> DL-0009/DL-0017, DL-0018, **DL-0024**, **DL-0029**, **DL-0044** e **DL-0049** são as de reversão
> não-barata.

## Todas as decisões

| DL | Fase | Título | Conf. | Rev. |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | 0 | Manter pacote base `com.fksoft` | Alta | Cara |
| [DL-0002](DL-0002-stack-versoes-backend.md) | 0 | Versões do stack backend (Spring Boot 3.5.16, Modulith 1.4.12, Java 21) | Alta | Moderada |
| [DL-0003](DL-0003-stack-frontend-fase-0.md) | 0 | Stack frontend Fase 0 (Angular 22 + ngx-translate; PrimeNG/Tailwind adiados) | Alta | Barata |
| [DL-0004](DL-0004-maven-wrapper-bootstrap.md) | 0 | Bootstrap do Maven Wrapper sem Maven no sistema | Alta | Barata |
| [DL-0005](DL-0005-adr-0014-ausente-adiar-fase-1.md) | 0 | ADR 0014 ausente: ~~adiar~~ → **criado** a pedido do dono (ver ADR 0014) | Alta | Barata |
| [DL-0006](DL-0006-modulith-detection-strategy.md) | 0 | Spring Modulith detection-strategy=explicitly-annotated | Alta | Barata |
| [DL-0007](DL-0007-accounts-cadastros-opcionais.md) | 1 | Accounts: CADASTUR/IATA opcionais no v1 (nenhum cadastro obrigatório) | Média | Barata |
| [DL-0008](DL-0008-exchange-nome-do-modulo.md) | 1 | Manter o nome `Exchange` para o módulo de câmbio (Q1) | Alta | Moderada |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | 1 | Quoting: preço = base BRL + markup (default 0); base comissionável em BRL | Média | **Cara** |
| [DL-0010](DL-0010-booking-quote-multiplicidade.md) | 1 | Booking: Quote→Booking não 1:1 no v1 (localizador é a trava) | Média | Moderada |
| [DL-0011](DL-0011-reconciliation-tolerancia-discrepancia.md) | 1 | Reconciliation: tolerância = max(R$1,00; 0,5% do spread esperado) | Média | Barata |
| [DL-0012](DL-0012-compliance-requirements-catalog.md) | 2 | Compliance: catálogo `entryType × DocumentRequirement` (seed da tabela 7.7, com fase) | Média | Barata |
| [DL-0013](DL-0013-finance-multimoeda-no-razao.md) | 2 | Finance: razão em moeda original (sem conversão); período agrega por moeda | Média | Moderada |
| [DL-0014](DL-0014-finance-comprar-vs-construir.md) | 2 | Finance: construir o seam mínimo (AP/AR+período) agora; contabilidade plena = comprar depois | Alta | Moderada |
| [DL-0015](DL-0015-compliance-filestorage-port.md) | 2 | Compliance: porta `FileStorage` + adaptador filesystem; hash SHA-256 | Alta | Barata |
| [DL-0016](DL-0016-inbound-webhook-signature-hmac.md) | 3 | Webhook de entrada: assinatura HMAC-SHA256 com segredo compartilhado (`X-Signature`) | Média | Moderada |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | 3 | Inbound: Account inexistente **rejeita** (422); não cria provisória nem enfileira | Baixa | Moderada |
| [DL-0018](DL-0018-integrated-quote-modeling.md) | 3 | Quote INTEGRATED reusa o agregado; colunas de composição MANUAL viram nulas | Alta | Moderada |
| [DL-0019](DL-0019-acl-resilience-scope-inbound.md) | 3 | ACL de entrada: classificação de falha + observabilidade (sem circuit breaker — não há chamada de saída) | Alta | Barata |
| [DL-0020](DL-0020-cancellation-lives-in-booking-module.md) | 4 | Cancelamento rico vive no módulo `booking` (sem módulo `cancellation`/`policy` novo) | Alta | Moderada |
| [DL-0021](DL-0021-merchant-of-record-attribute-default-affiliate.md) | 4 | Merchant of record é atributo por marca/contrato; default afiliado (costBearer=SUPPLIER) | Alta | Moderada |
| [DL-0022](DL-0022-penalty-currency-no-conversion.md) | 4 | Multa/encargos na moeda original (sem conversão cambial nesta fase) | Média | Barata |
| [DL-0023](DL-0023-no-show-waiver-proof-flag.md) | 4 | No-show: dispensa por voo cancelado via flag de prova rastreável (conformidade = Compliance) | Média | Barata |
| [DL-0024](DL-0024-charges-are-distinct-facts-never-netted.md) | 4 | Encargos são fatos distintos que **nunca** se compensam (armadilha do merchant) | Alta | **Cara** |
| [DL-0025](DL-0025-market-rate-source-port-and-manual.md) | 5 | Taxa de mercado: porta `MarketRateProvider` + registro manual de contingência (v1) | Média | Moderada |
| [DL-0026](DL-0026-freeze-scope-global-per-pair.md) | 5 | Escopo do congelamento: global por par de moeda (v1) | Média | Moderada |
| [DL-0027](DL-0027-drift-alert-threshold-2pct.md) | 5 | Limite de alerta de drift = \|drift\| > 2% da exposição estrangeira aberta do livro | Média | Barata |
| [DL-0028](DL-0028-fxposition-open-on-booking-confirmed.md) | 5 | `FxPosition` abre em `BookingConfirmed`; fecha reusando a liquidação de Reconciliation (sem duplicar o per-case) | Alta | Moderada |
| [DL-0029](DL-0029-rep-type-target-rep-p-export-upload.md) | 6 | Tipo de REP (Q6): mirar REP-P; AFD/AEJ como upload da exportação oficial (serve REP-C via USB) | **Baixa** | **Cara** |
| [DL-0030](DL-0030-people-module-owns-snapshot-history.md) | 6 | Novo módulo `people` é dono do snapshot/idempotência/histórico; crawler técnico em `infra/integration`; sem módulo `platform` vazio | Alta | Moderada |
| [DL-0031](DL-0031-circuit-breaker-queue-retry-in-process.md) | 6 | Disjuntor + fila de retry + dead-letter in-process (sem resilience4j); origem do REP atrás de porta com mock injetor de falhas | Alta | Moderada |
| [DL-0032](DL-0032-afd-signature-integrity-check.md) | 6 | Verificação de assinatura/integridade do AFD na ingestão (envelope CAdES/PKCS#7 + hash); ICP-Brasil completo fica p/ Platform (SPEC-0023) | Média | Moderada |
| [DL-0033](DL-0033-crawl-period-and-multi-source.md) | 6 | Periodicidade diária, período corrente `YYYY-MM`; lista de `sourceRef` configurável (default um) | **Baixa** | Barata |
| [DL-0034](DL-0034-promofx-subject-is-agency-event-derived.md) | 7 | PromoFxAdvisor: sujeito = agência (derivado de evento, não rota); intelligence é consumidor-folha | Média | Moderada |
| [DL-0035](DL-0035-promofx-verdict-thresholds-and-deterministic-advisor.md) | 7 | PromoFxAdvisor: advisor determinístico; limites CONVERTE × QUEIMA_MARGEM (MIN_VOLUME=5, BURN=R$1.000) + ganho estimado | Média | Barata |
| [DL-0036](DL-0036-overridenudge-gated-flag-and-llm-port-seam.md) | 7 | OverrideNudge desligado por feature flag (Q4); seam de porta LLM desenhado mas **não** wired; recomputação on-event | Média | Barata |
| [DL-0037](DL-0037-parameter-rule-model-and-precedence-engine.md) | 8a | `ParameterRule` (colunas de escopo, não jsonb) + motor de precedência: camada→especificidade→`validFrom`/`createdAt`/`id` (desempate determinístico) | Alta | Moderada |
| [DL-0038](DL-0038-runtime-self-service-authz-and-directive-audit.md) | 8a | Q8: diretor/admin editam parâmetros e diretivas em runtime (self-service auditável); fluxos não. Diretiva = papel diretor + justificativa + evento (403 sem papel) | Média | Barata |
| [DL-0039](DL-0039-governed-parameter-keys-seed-and-q5-agent-commission.md) | 8a | Seed SYSTEM_DEFAULT só das chaves já usadas (MARKUP_PCT=0, FX_DRIFT_LIMIT=2%, RECON_DISCREPANCY_TOL=R$1,00); Q5 (comissão do agente) comportada pelo motor por escopo, sem implementar Commissioning | Média | Barata |
| [DL-0040](DL-0040-markup-provider-graduation-keeps-contract.md) | 8a | Gradua `MarkupProvider`: motor real preservando contrato (`currentMarkup()` + sobrecarga com escopo); source = camada vencedora; sem regra → SYSTEM_DEFAULT (back-compat) | Alta | Moderada |
| [DL-0041](DL-0041-finance-event-driven-ap-ar-posting.md) | 8b | Finance posta AP/AR automático ao consumir `CancellationCharged`/`NoShowCharged`/`MerchantObligationIncurred` (booking), idempotente por UNIQUE `(source_ref, charge_kind)` + state-check; comissão/SupplierSettlement diferidos (sem produtor) | Média | Moderada |
| [DL-0042](DL-0042-finance-buy-vs-build-full-gl-reaffirmed.md) | 8b | Finance: reafirma comprar-vs-construir — entrega "full" é livro-caixa (AP/AR+período+evento+balancete), **não** GL pleno (plano de contas/partidas dobradas/DRE/SPED = comprar/integrar) | Alta | Moderada |
| [DL-0043](DL-0043-finance-trial-balance-per-currency.md) | 8b | Finance: `GET /periods/{yyyymm}/trial-balance` por moeda e por status (net operacional = AR−AP), sem plano de contas; endpoint novo aditivo (não muda `GET /periods`) | Alta | Barata |
| [DL-0044](DL-0044-billing-tax-regime-simples-and-swappable-strategy.md) | 8c | Billing: regime **Simples Nacional** (default) + emitente = Acme; ISS/retenções parametrizados por regime+município atrás de `TaxRegimeStrategy` trocável (Q7) | **Baixa** | **Cara** |
| [DL-0045](DL-0045-billing-module-and-taxable-base-is-commission.md) | 8c | Billing: novo módulo `domain.billing` (13º); `CommissionInvoice`; **base tributável = comissão** (nunca o pacote); referência ao lançamento por id+porta, sem FK | Alta | Moderada |
| [DL-0046](DL-0046-nfse-municipal-acl-port-and-traceable-mock.md) | 8c | Billing: NFS-e municipal como porta `NfseGateway` + adaptador ACL com **mock rastreável** em `infra.integration.nfse`; assinatura e-CNPJ via porta `CertificateSigner` (stub → Platform/SPEC-0023); falha classificada (TIMEOUT/UNAVAILABLE→502, REJECTED→422) | Média | Moderada |
| [DL-0047](DL-0047-billing-issue-idempotency-finance-event-and-compliance-archive.md) | 8c | Billing: emissão idempotente por comissão (UNIQUE parcial); arquiva NFS-e no Compliance via orquestrador `infra`; Finance lança o tributo consumindo `CommissionInvoiceIssued` (idempotente); `billing` é módulo **folha** (grafo acíclico) | Média | Moderada |
| [DL-0048](DL-0048-payout-payment-gateway-acl-async-webhook.md) | 8d | Payout: porta `PaymentGateway` + adaptador **mock rastreável com webhook assíncrono** (ADR 0006) em `infra.integration.payment`; idempotente por `(payoutId, installmentSeq, providerRef)`; falha classificada (sem "pago" falso); DTO do provedor não vaza (ArchUnit) | **Baixa** | Moderada |
| [DL-0049](DL-0049-payout-foreign-settlement-rate-and-brl-baixa.md) | 8d | Payout: liquidação do fornecedor modela `amount` (USD) + `settlementRate` (escala 6, >0) + `settledBrl` (baixa em BRL = amount × rate, HALF_UP); remessa internacional real adiada (mesmo gateway) | **Baixa** | **Cara** |
| [DL-0050](DL-0050-payout-installments-no-interest-and-exact-cent-distribution.md) | 8d | Payout: parcelamento **v1 sem juros**; Σ parcelas == total exato (resto de centavos na 1ª parcela); cada parcela executa/comprova; Payout só `EXECUTED` quando todas executam; "sem plano" = 1 parcela implícita | Média | Moderada |
| [DL-0051](DL-0051-payout-supplier-settled-consumed-by-finance-leaf-acyclic.md) | 8d | Payout folha: `SupplierSettled` consumido pelo **Finance** (listener idempotente, posta uma vez); Reconciliation/Exchange seguem fechando FX pela liquidação própria (costura ao evento adiada, sem ciclo); REFUND não cancela a obrigação do fornecedor (DL-0024) | Média | Moderada |
| [DL-0052](DL-0052-aftersales-sla-from-commercial-policy.md) | 8e | AfterSales: SLA = parâmetro governado resolvido pela CommercialPolicy (chaves `AFTERSALES_SLA_FIRST_RESPONSE`=24h/`_RESOLUTION`=72h/`_REFUND`=48h, NUMBER horas; seed SYSTEM_DEFAULT V23); Diretiva pode sobrepor sem deploy | Média | Barata |
| [DL-0053](DL-0053-aftersales-sla-breach-job-and-cost-to-serve.md) | 8e | AfterSales: breach por job de **relógio controlado** (`markBreaches(now)`, instante como parâmetro, padrão do Booking); breach é **flag/alerta** (não bloqueia, idempotente); custo de servir = `CostToServe` (Money BRL acumulável: handling+refund+reaberturas) | Média | Barata |
| [DL-0054](DL-0054-aftersales-orchestrates-cancel-and-refund-via-facades.md) | 8e | AfterSales orquestra via **fachadas** (`PayoutService.create` REFUND com `originRef`=caseId; `BookingService.cancel`), **idempotente** por `linkedPayoutId` (não cria 2 Payouts); BR6 — não muda reserva nem lança financeiro; armadilha do merchant intacta; grafo **acíclico** | Média | Moderada |
