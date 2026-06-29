# Caderno de testes — Slice 8c-2 · Billing: emissão NFS-e (ACL + arquivamento + lançamento) (SPEC-0016)

## Escopo

Emissão da NFS-e de comissão ponta a ponta (**BR3/BR4/BR5/BR7**; DL-0046/DL-0047): porta de domínio
**`NfseGateway`** + adaptador **ACL com mock rastreável** (`SimulatedMunicipalNfseService` em
`infra.integration.nfse`, com envelope externo `MunicipalNfseEnvelope` que **não vaza** para o
domínio), assinatura e-CNPJ via porta **`CertificateSigner`** (stub → SPEC-0023), orquestrador
**`BillingIssuanceService`** (infra, isento da regra de ciclo) que: calcula tributos (DL-0044),
transmite (classificando falha), **arquiva** o XML assinado no Compliance (`COMMISSION_INVOICE`,
XAdES, dado pessoal) anexado ao lançamento de comissão, e registra a emissão. O **Finance lança o ISS**
(PAYABLE `TAX_PAYABLE`) consumindo o evento `CommissionInvoiceIssued` (idempotente, `finance → billing`
acíclico). Novo `EntryType.TAX_PAYABLE`; nova regra ArchUnit (vendor NFS-e não cruza o domínio).

## Casos de teste

### Integração (Testcontainers + prefeitura fake) — `BillingIssuanceIntegrationTest` (4 casos)
| Caso | Verifica | Regra |
|---|---|---|
| issuingCommissionOf405ComputesIssArchivesXmlAndPostsTheTaxToFinance | comissão R$ 405 → EMITIDA, número/código, **ISS R$ 20,25**, documento `COMMISSION_INVOICE` XAdES dado-pessoal arquivado e **anexado** ao lançamento, **ISS lançado** no Finance (PAYABLE TAX_PAYABLE 20,25) | **Acceptance Criteria**, BR3/BR5, DL-0047 |
| reIssuingTheSameInvoiceDoesNotDuplicateNumberDocumentOrTaxPosting | reemitir → **um** número, **um** documento, **um** lançamento de tributo | **BR4** (idempotência de emissão) |
| municipalityRejectionIsClassifiedAs422AndNeverIssued | município `REJECT` → `BillingMunicipalityRejectedException` (422); fica RASCUNHO; **nada** arquivado | **BR7** (rejeição classificada, sem "emitida" falsa) |
| webserviceTimeoutIsClassifiedAs502AndNeverIssued | município `TIMEOUT` → `BillingNfseWebserviceException` (502); fica RASCUNHO | **BR7** (falha de webservice classificada) |

### Integração — regressão de ouro — `BillingSatisfiesDocumentRequirementIntegrationTest` (1 caso)
| Caso | Verifica | Regra |
|---|---|---|
| issuedNfseSatisfiesTheCommissionEntryRequirementSoTheMonthCanClose | lançamento `COMMISSION_RECEIVABLE` em 2026-07: **antes** de emitir o fechamento é **vetado** (`FinancePeriodCannotCloseException`); **depois** de emitir a NF (que arquiva+anexa o documento), o **mês fecha** | **BR5** + Tests Required (falha antes, passa depois) |

### Arquitetura — `ArchitectureTest`
| Caso | Verifica | Regra |
|---|---|---|
| DOMAIN_MUST_NOT_DEPEND_ON_NFSE_ADAPTER | nenhuma classe de `..domain..` depende de `..infra.integration.nfse..` (envelope do vendor não vaza) | ACL (SPEC-0016 BR3, DL-0046) |
| ModularityTests.verifiesModularStructure | grafo acíclico com `finance → billing` (consumo de evento) + `infra.nfse → billing/compliance` | Spring Modulith |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 261, Failures: 0, Errors: 0, Skipped: 0`
(+6 da fatia, 255 → 261). Portões verdes: ArchUnit (11 regras, incl. a nova NFS-e), Spring Modulith
acíclico, Spotless, Checkstyle. Mock rastreável injeta falha por código de município (`REJECT`/`TIMEOUT`).

## Cobertura — o que NÃO está coberto e por quê

- **Webservice municipal real / XML ABRASF completo:** fora de escopo (DL-0046) — mock rastreável.
- **Custódia real do e-CNPJ:** porta `CertificateSigner` com stub; dono futuro = Platform (SPEC-0023).
- **Estorno contábil da nota cancelada no Finance:** fora de escopo da SPEC-0016 (DL-0047); o
  cancelamento da NF (BR6) e a API são exercidos na Slice 8c-3.
- **Retenções no Finance:** o listener já posta retenções (PAYABLE `TAX_PAYABLE` por kind), mas o
  default Simples não gera retenções (DL-0044); a costura é provada pelo swap-test unitário (8c-1).

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply
cd backend && ./mvnw -Dtest='BillingIssuanceIntegrationTest,BillingSatisfiesDocumentRequirementIntegrationTest' test
cd backend && ./mvnw verify   # build completo + portões (Docker no ar)
```
