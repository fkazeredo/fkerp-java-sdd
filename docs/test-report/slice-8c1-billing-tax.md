# Caderno de testes — Slice 8c-1 · Billing: cálculo de tributos + agregado (SPEC-0016)

## Escopo

Fundação do módulo **`com.fksoft.domain.billing`** (13º módulo Modulith, folha): o agregado
**`CommissionInvoice`** (base = comissão, ciclo RASCUNHO→EMITIDA→CANCELADA) e o **cálculo de tributos
parametrizado por regime** (DL-0044): porta `TaxRegimeStrategy` + `SimplesNacionalTaxStrategy`, com a
alíquota de ISS por município via porta `MunicipalIssRateProvider` (tabela seedada). Cobre:
**BR1** (base tributável = comissão, nunca o pacote), **BR2/BR2a** (ISS = alíquota × base, HALF_UP;
regime trocável), **BR4** (transições de estado). Migração **V20** (`commission_invoices` com UNIQUE
parcial por comissão; `municipal_iss_rates` seedada). i18n `billing.*`; exceções no `HttpErrorMapping`.

## Casos de teste

### Unitário — `TaxRegimeStrategyTest` (5 casos)
| Caso | Verifica | Regra |
|---|---|---|
| simplesNacionalIssIsRateTimesCommissionBaseHalfUp | base R$ 405,00 × 5% = **R$ 20,25** (Acceptance Criteria) | BR2a, DL-0044 |
| simplesNacionalUsesTheMunicipalRateForSaoPaulo | São Paulo `3550308` (2%) → 405,00 × 0,02 = **R$ 8,10** | BR2, DL-0044 |
| simplesNacionalRoundsHalfUp | 333,33 × 0,05 = 16,6665 → HALF_UP → **R$ 16,67** | money kernel (escala 2 HALF_UP) |
| simplesNacionalHasNoFederalWithholdings | Simples optante → **sem** retenções federais (IN RFB 1.234/2012) | BR2a, DL-0044 |
| theRegimeIsASwappableStrategy | plugar stub "Presumido" (IRRF 1,5% → R$ 6,08) muda o resultado **sem tocar** o agregado/serviço | DL-0044 (estratégia trocável) |

### Unitário — `CommissionInvoiceTest` (5 casos)
| Caso | Verifica | Regra |
|---|---|---|
| draftIsBornWithTheCommissionAsBaseNeverThePackage | rascunho nasce com base = **comissão R$ 405** (o pacote R$ 2.700 **não tem campo** no agregado) | **BR1** (regressão: base = comissão) |
| issueMovesDraftToIssuedWithNumberAndTax | RASCUNHO→EMITIDA grava número/código/ISS | BR3/BR4 |
| cannotIssueAnAlreadyIssuedInvoice | reemitir EMITIDA → `BillingInvoiceTransitionInvalidException` (409) | **BR4** (idempotência de estado) |
| cancelMovesIssuedToCancelled | EMITIDA→CANCELADA | BR6 |
| cannotCancelADraft | cancelar RASCUNHO → exceção de transição | BR6 |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 255, Failures: 0, Errors: 0, Skipped: 0`
(+10 da fatia, 245 → 255). Portões verdes: **ArchUnit** (10), **Spring Modulith** acíclico (13º módulo
Billing detectado e verificado), **Spotless** (arquivos limpos após `spotless:apply`), **Checkstyle**
(0 violações). Migração **V20** aplicada pelo Flyway nos testes de integração (Postgres real).

## Cobertura — o que NÃO está coberto e por quê

- **Emissão real (transmissão, assinatura, arquivamento, lançamento no Finance):** é a Slice 8c-2 (ACL
  + orquestrador). Aqui só o cálculo puro e o agregado.
- **Regimes Presumido/Real completos:** fora de escopo (Rule Zero / DL-0044) — só o default Simples real
  + a costura trocável (provada pelo swap-test com stub).
- **UNIQUE parcial por comissão sob concorrência:** o índice existe (V20) e o `createDraft` trata a
  corrida; a prova ponta a ponta de idempotência de emissão é a Slice 8c-2 (integração).

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply
cd backend && ./mvnw -Dtest='TaxRegimeStrategyTest,CommissionInvoiceTest' test   # só os unitários da fatia
cd backend && ./mvnw verify                                                       # build completo + portões (Docker no ar)
```
