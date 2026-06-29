# Caderno de testes — Slice 8b · Quoting ramo INTEGRATED (SPEC-0009 / SPEC-0005)

## Escopo

Ativa o gancho adormecido do `Quote` (redesenho 7.6): o ramo **INTEGRATED**. Novo factory
`Quote.composeIntegrated` (preço externo confiável: `suggested == applied == externo`, **sem** rodar
o motor de sugestão, **sem** comissão/markup/câmbio, **sem** OverrideRecord); `applyOverride` recusa
INTEGRATED (BR2); porta cross-módulo `QuoteIntegrationPort` (consumida pela ACL de Sourcing na 8c);
migração `V10__quotes_integrated_and_source_offer.sql` (colunas de composição MANUAL viram nulas +
`source_offer_id`). Decisão aplicada: **DL-0018**.

## Casos de teste

### Unitário / domínio — `QuoteAggregateTest` (+2 casos, total 5)
| Caso | Verifica | Regra |
|---|---|---|
| composesIntegratedTrustingTheExternalPriceWithoutSuggestionEngine | `composeIntegrated` → INTEGRATED, suggested==applied==480, sem override; FX/comissão/markup nulos; view com seções MANUAL nulas | BR2 / DL-0018 |
| refusesOverrideOnIntegratedQuote | `applyOverride` em INTEGRATED → `QuoteOverrideNotApplicableException`; applied inalterado | BR2 |
| (regressão MANUAL) chains/rejects… | os 3 casos MANUAL existentes seguem verdes (compose preenche tudo) | BR5/BR6/BR7 |

### Integração (Testcontainers/Postgres) — `IntegratedQuoteIntegrationTest` (2 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| createsIntegratedQuoteTrustingTheExternalPriceWithoutSuggestionOrOverride | `QuoteIntegrationPort.createIntegratedQuote` persiste; `GET /api/quotes/{id}` → INTEGRATED, applied=preço externo, sem sugestão/comissão/override | "cria Quote INTEGRATED com appliedAmount = preço recebido, sem sugestão e sem override" |
| refusesOverrideOnIntegratedQuoteWith409 | `POST /override` em INTEGRATED → 409 `quoting.override.not-applicable` | BR2 (não há divergência contra sugestão) |

### Regressão de proveniência (SPEC-0005)
A `QuoteIntegrationTest` (5 casos MANUAL) permanece verde: a imutabilidade da proveniência MANUAL e a
matemática de composição não foram afetadas pela migração (as colunas agora aceitam nulo, mas o
factory MANUAL continua preenchendo todas).

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 118, Failures: 0, Errors: 0,
Skipped: 0` (+4 da fatia). Portões verdes: ArchUnit, Spring Modulith (10 módulos), Spotless,
Checkstyle, completude do `HttpErrorMapping` (nova exceção 409 mapeada).

## Cobertura — o que NÃO está coberto (e por quê)

- **Webhook de entrada / tradução ACL / idempotência / `IntegratedQuoteCreated` / health do
  conector:** Slice 8c (esta fatia entrega só o ramo do Quote + a porta).
- **Recomposição do preço integrado:** adormecida por decisão de produto (redesenho Parte 4.3;
  DL-0018 deixa o caminho de reversão aditivo).

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=QuoteAggregateTest                         # unit
cd backend && ./mvnw verify -Dtest=IntegratedQuoteIntegrationTest            # integração (Docker up)
cd backend && ./mvnw verify                                                 # tudo + portões
```
