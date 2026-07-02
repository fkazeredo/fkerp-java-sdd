# DL-0117 — enum→cadastro fatia 18c: Sourcing / Exchange / Booking / Compliance (reuso do padrão DL-0115)

- **Fase:** 18c (conversão dos enums de referência de Sourcing/Exchange/Booking/Compliance)
- **Spec(s):** SPEC-0031 (cadastro); SPEC-0009 (Sourcing), SPEC-0011 (Exchange), SPEC-0010 (Booking), SPEC-0008 (Compliance)
- **ADR relacionado:** ADR-0019 (padrão enum→cadastro)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

As fatias 18a/18b criaram o módulo `cadastro` e o padrão enum→cadastro (DL-0115/DL-0116),
convertendo Admin/Assets/Billing e Marketing/Intelligence/Portfolio. Faltava converter os grupos de
referência de **Sourcing, Exchange, Booking e Compliance**, reusando exatamente o mesmo mecanismo,
sem quebrar o contrato de fio e **preservando a lógica de domínio que ramifica** por valores
específicos — o ramo INTEGRATED do quoting (DL-0018), as janelas de multa + a armadilha do lojista
(DL-0024/DL-0010) e a retenção legal + o close-check de compliance (DL-0012).

## Decisão

Reusa integralmente o padrão de DL-0115. Enums convertidos nesta fatia:

| Módulo     | CadastroType         | Codes (semeados em V35)                                            | Ramificação preservada |
|------------|----------------------|-------------------------------------------------------------------|------------------------|
| sourcing   | `OFFER_ORIGIN`       | PORTAL_API, EXTERNAL_SITE, THIRD_PARTY_CATALOG, RAW_DEMAND         | `OfferOriginCodes.EXTERNAL_SITE` (procedência gravada pelo ACL inbound) |
| sourcing   | `INTEGRATION_LEVEL`  | NONE, INBOUND, BIDIRECTIONAL                                       | `IntegrationLevelCodes.INBOUND` — o ramo INTEGRATED do quoting (DL-0018) cunha uma oferta neste nível |
| exchange   | `MARKET_RATE_SOURCE` | FEED, MANUAL                                                       | `MarketRateSourceCodes` (produzido pelo sistema; o controller de contingência grava MANUAL — DL-0025) |
| booking    | `CHARGE_KIND`        | PENALTY, SUPPLIER, CUSTOMER_REFUND, NO_SHOW                       | `ChargeKindCodes` — a postagem AP/AR (Finance) ramifica por estes quatro (SPEC-0015 BR5) |
| booking    | `CANCELLATION_TYPE`  | STANDARD, ALL_SALES_FINAL, CUSTOM                                 | `CancellationTypeCodes.usesWindows/isAllSalesFinal` — janelas de multa + armadilha do lojista (DL-0024/DL-0010) |
| compliance | `DOCUMENT_TYPE`      | NFE, NFSE, RPA, UTILITY_BILL, LOAN_CONTRACT, COMMISSION_INVOICE, PAYMENT_PROOF, REFUND_PROOF, PAYROLL, TIME_RECORD_AFD, PROCESSED_JOURNAL_AEJ, VOUCHER, REPRESENTATION_CONTRACT, OTHER | `DocumentTypeCodes` — retenção FISCAL(5a)/CONTRACT(10a) (`RetentionPolicy`) + os dois tipos legais do AFD/AEJ |
| compliance | `SIGNED_FORMAT`      | CAdES_P7S, XADES, PADES                                            | `SignedFormatCodes` (produzido pelo adaptador ingestor; NÃO validado na escrita) |
| compliance | `REQUIREMENT_PHASE`  | AT_REGISTRATION, AT_SETTLEMENT                                     | `RequirementPhaseCodes.AT_REGISTRATION` — o close-check só considera esta fase (DL-0012) |

1. **Representação persistida:** cada campo `@Enumerated(STRING)` vira **`String code`** (mesma
   coluna, mesmo valor = nome do antigo constante). Contrato JSON **idêntico** (era string de enum,
   continua string). Inclui o `@Id`/`@IdClass` `phase` do `DocumentRequirement` (coluna já era
   string).
2. **DTOs/views/eventos:** os campos viram `String`. Requests de escrita usam `@NotBlank String`
   (Sourcing origin/integrationLevel; Booking cancellation type; Compliance document type via
   `@RequestParam`).
3. **Validação (só onde há escrita a partir do fio):** Sourcing valida `OFFER_ORIGIN` e
   `INTEGRATION_LEVEL` no `register`; Booking valida `CANCELLATION_TYPE` no PUT da política; Compliance
   valida `DOCUMENT_TYPE` no `upload`. Tudo via a porta `CadastroValidator` (código inválido/inativo →
   `CadastroCodeInvalidException`, 422).
4. **Produzidos pelo sistema (sem validação na escrita):** `MARKET_RATE_SOURCE` (o source nunca chega
   como payload — o controller grava MANUAL) e `SIGNED_FORMAT` (o formato é produzido pelo adaptador
   ingestor — XADES no NFS-e, CAdES_P7S no AFD/AEJ). Ambos são cadastros para que o rótulo seja
   editável e as telas mostrem o label — mesmo precedente dos `INSIGHT_*` (DL-0116). Nota técnica: o
   `CadastroValidator` normaliza o code para MAIÚSCULA; o valor cablado `CAdES_P7S` é intencionalmente
   misto, então validá-lo na escrita quebraria — outro motivo para não validá-lo.
5. **Direção da dependência (grafo acíclico):** `sourcing`/`booking`/`compliance` → `cadastro`
   (porta). O `cadastro` continua folha; Modulith acíclico. O `LegalTimeRecordArchived` (módulo
   `people`) deixa de importar `compliance.DocumentType` (passa a `String`) — **remove** uma
   dependência de tipo cross-módulo.
6. **Lógica que ramifica (preservada por constantes):** classes `*Codes` no próprio módulo guardam só
   o comportamento cablado. O `switch` exaustivo do `BookingChargeEventsListener` (Finance) sobre o
   enum `ChargeKind` vira `switch` sobre as constantes de code com `default` seguro (código
   desconhecido não posta nada — preserva a não-compensação/armadilha). `CancellationPolicy` guarda o
   `usesWindows`/`allSalesFinalCostBearer` via `CancellationTypeCodes`. `RetentionPolicy` mapeia o
   code do documento para FISCAL/CONTRACT com fallback FISCAL seguro.
7. **Rótulo nas telas:** o `CadastroLabelPipe`/`CadastroLabelService` (18b) é aplicado nas telas
   Sourcing (origem/nível), Exchange-desk (source), Booking/Cancellation (tipo) e Compliance
   (documento/formato assinado). A severidade das tags continua usando o **code** cru.
8. **Migração V35** semeia os 8 tipos (37 itens) como itens (`code`=nome do enum, `label` pt-BR),
   idempotente (`ON CONFLICT DO NOTHING`).

## Justificativa

- **Invariante do dono:** "o valor persistido vira `code` validado com `code`=nome do enum ⇒ JSON de
  contrato inalterado". Mantido byte-a-byte (provado por testes de round-trip).
- **Regra Zero:** um único mecanismo (registry + porta) cobre também estes grupos; as constantes
  `*Codes` existem só onde há ramificação real.
- **Confiança=Alta:** mecânico e testável (round-trip idêntico; rejeição de código inválido/inativo;
  ramificação preservada — armadilha do lojista, ramo INTEGRATED, retenção). Backend `./mvnw verify`
  verde: **507 testes** (503 + 4 do novo teste de invariante 18c), cobertura e ArchUnit intactos.

## Alternativas descartadas

- **Validar também `MARKET_RATE_SOURCE`/`SIGNED_FORMAT` na escrita.** Descartada: não há escrita a
  partir do fio (são produzidos pelo sistema/adaptador); validar não agrega e, no caso do
  `CAdES_P7S` misto, colidiria com a normalização do validador.
- **Converter a ramificação (tipo de cancelamento/kind/retenção) para dado sem constantes.**
  Descartada: perderia o comportamento determinístico. Mesmo compromisso de DL-0115: cadastro =
  conjunto+rótulos; constantes = comportamento cablado.
- **Converter os enums de estado/técnicos/legais adjacentes** (`BookingStatus`, `CostBearer`,
  `IntegrationFailureClass`, `LegalType`/`LegalBasis`). Descartada por ADR-0019 (nunca convertidos).

## Impacto

- **Specs:** SPEC-0031 (tabela de tipos 18c marcada como entregue).
- **Arquivos:** enums removidos + `*Codes` novos em `sourcing`/`exchange`/`booking`/`compliance`;
  entidades/DTOs/views/eventos/repos/serviços retipados para `String`; `CadastroType` +8 valores;
  controllers (`SourcingController` via DTO, `MarketRateController`, `CancellationPolicyAdminController`
  via DTO, `ComplianceController`, `PointAfdController`) e infra (`AfdIngestionService`,
  `BillingIssuanceService`, `PayoutExecutionService`, `PayslipArchivingService`) usam os codes;
  `BookingChargeEventsListener` (Finance) e `LegalTimeRecordArchived` (People) retipados. Frontend:
  `cadastro.models` (+8 tipos), telas Sourcing/Exchange/Booking/Compliance com o pipe.
- **Migração:** **V35** semeia os 8 tipos (37 itens), idempotente.
- **Contratos:** **sem mudança de fio** — campos convertidos continuam `string`; sem novos endpoints.
  OpenAPI/pom → 0.31.0 (MINOR, ADR-0015).

## Como reverter

Retipar os campos/DTOs de volta aos enums, remover as constantes `*Codes` e a validação, e apagar as
linhas do seed em V35 (migração de baixa). Moderada: os valores no banco são idênticos aos nomes dos
enums, então não há backfill — só refator de tipos.
