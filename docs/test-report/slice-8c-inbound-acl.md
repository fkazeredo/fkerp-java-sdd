# Caderno de testes — Slice 8c · ACL de entrada do site de cotação (SPEC-0009)

## Escopo

A **primeira ACL real**: o webhook do site de cotação. Verificação de assinatura HMAC-SHA256
(DL-0016), tradução do payload externo → comando de domínio na borda (BR6; o DTO externo vive só em
`infra.integration.quotationsite`), criação de **Quote INTEGRATED** (preço externo confiável, sem
recompor — BR2), idempotência por `externalQuotationId` (BR4), resolução da Account por documento
(rejeita 422 se inexistente — DL-0017), classificação de falha (`IntegrationFailureClass`),
observabilidade (log de integração + health do conector), evento `IntegratedQuoteCreated`. Migração
`V11__create_inbound_quotations.sql`. Decisões: DL-0016, DL-0017, DL-0018, DL-0019. Cobre todos os
Acceptance Criteria da SPEC-0009.

## Casos de teste

### Unitário — `QuotationSiteSignatureVerifierTest` (6 casos)
| Caso | Verifica | Regra |
|---|---|---|
| acceptsAValidSignature / …WithSha256Prefix | HMAC válido (com e sem prefixo `sha256=`) passa | BR3/DL-0016 |
| rejectsAMissingSignature | header ausente/em-branco → exceção | BR3 |
| rejectsATamperedBody | corpo adulterado → assinatura inválida | BR3 |
| rejectsAWrongSecret | assinatura com outro segredo → inválida | BR3 |
| rejectsANonHexSignature | header não-hex → inválida | BR3 |

### Unitário — `QuotationSiteInboundAdapterTest` (4 casos) — a tradução ACL
| Caso | Verifica | Regra |
|---|---|---|
| verifiesAndTranslatesAValidPayloadToADomainCommand | payload externo → `RegisterInboundQuotationCommand` (nenhum DTO externo retorna) | BR6 |
| rejectsAnInvalidSignatureBeforeTranslating | assinatura inválida barra antes de traduzir | BR3 |
| rejectsAMalformedJsonPayload | JSON malformado → `integration.payload.invalid` | validação |
| rejectsAnIncompletePayload | campos faltando → `integration.payload.invalid` | validação |

### Integração (Testcontainers/Postgres) — `QuotationSiteInboundIntegrationTest` (6 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| validWebhookCreatesAnIntegratedQuoteWithoutSuggestionOrOverride | webhook assinado → 202; Quote INTEGRATED, applied=preço externo, sem comissão/override | "cria Quote INTEGRATED, sem sugestão e sem override" |
| reDeliveryOfTheSameExternalIdIsIdempotent | reentrega do mesmo id → mesmo quoteId; 1 quote no banco | "reenviar a mesma cotação não duplica" (BR4) |
| invalidSignatureIsRejectedAndNothingIsCreated | assinatura inválida → 401 `integration.signature.invalid`; 0 quotes | "assinatura inválida é rejeitada e nada é criado" (BR3) |
| invalidPayloadIsRejectedWith400 | payload incompleto → 400 `integration.payload.invalid` | API Contracts (400) |
| unknownAccountDocumentIsRejectedWith422 | documento sem conta → 422 `integration.account.not-found`; 0 quotes | BR7/DL-0017 |
| connectorHealthReportsProcessedInboundQuotations | `GET /health` → connector=quotation-site, status=UP, total=1 | Observability (health do conector) |

### Arquitetura — `ArchitectureTest` (+1 regra)
| Caso | Verifica | Regra |
|---|---|---|
| DOMAIN_MUST_NOT_DEPEND_ON_EXTERNAL_INTEGRATION_DTOS | nenhuma classe de `..domain..` referencia `..infra.integration.quotationsite..` (o `ExternalQuotationPayload` não cruza) | BR6 / Acceptance "ArchUnit confirma que o DTO externo não cruza para o domínio" |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 135, Failures: 0, Errors: 0,
Skipped: 0` (+17 da fatia). Portões verdes: ArchUnit (regra de fronteira da ACL inclusa), Spring
Modulith (10 módulos), Spotless, Checkstyle (0 violações), completude do `HttpErrorMapping`
(3 exceções de integração novas mapeadas: 401/400/422).

## Cobertura — o que NÃO está coberto (e por quê)

- **Recomposição do preço integrado:** adormecida (redesenho Parte 4.3; DL-0018).
- **Circuit breaker/timeout/retry:** não se aplica — a ACL é de **entrada** (sem chamada de saída);
  decisão e justificativa em **DL-0019**. A 1ª ACL de saída (GDS/crawler) os introduz.
- **Consumidor de `IntegratedQuoteCreated`** (Intelligence, funil por canal 8.2-F): módulo ainda não
  existe; evento publicado in-process sem consumidor (harmoniza na SPEC-0013).
- **Tela Angular** do inbound: o webhook é máquina-a-máquina; não há jornada de tela.

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=QuotationSiteSignatureVerifierTest,QuotationSiteInboundAdapterTest  # unit
cd backend && ./mvnw verify -Dtest=QuotationSiteInboundIntegrationTest                                # integração (Docker up)
cd backend && ./mvnw verify                                                                          # tudo + portões
```
