# Caderno de testes — Slice 8a · Sourcing (SPEC-0009)

## Escopo

Módulo `Sourcing` (10º módulo Spring Modulith): agregado `SourcedOffer` (texto-livre do produto +
preço-base + origem + nível de integração + ref. externa), registro/consulta manual via
`POST /api/sourcing/offers` e `GET /api/sourcing/offers/{id}`, evento `OfferSourced`, migração
`V9__create_sourced_offers.sql`. Cobre a BR1 da SPEC-0009 e a parte de procedência manual dos
Acceptance Criteria. O ramo INTEGRATED (8b) e a ACL de entrada (8c) são fatias seguintes.

## Casos de teste

### Unitário / domínio — `SourcedOfferTest` (3 casos)
| Caso | Verifica | Regra |
|---|---|---|
| registersAFreeTextOfferWithProvenance | texto livre é oferta válida; preço/origem/nível/ref preservados | BR1 |
| trimsProductTextAndAcceptsNullExternalRef | `productText` é normalizado (trim); `externalRef` opcional | BR1 |
| rejectsBlankProductText | texto vazio/em-branco → `SourcedOfferInvalidException` | BR1 |

### Integração (Testcontainers/Postgres) — `SourcingIntegrationTest` (3 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| registersAndFetchesASourcedOffer | `POST /offers` → 201; `GET /offers/{id}` → 200 com a procedência | registrar procedência da oferta (Sourcing) |
| rejectsBlankProductTextWith400 | texto em branco → 400 (validação delivery + domínio BR1) | BR1 |
| returns404ForUnknownOffer | `GET` id inexistente → 404 `sourcing.offer.not-found` | API Contracts (404) |

### Arquitetura
`ArchitectureTest` + `ModularityTests` (Spring Modulith): o novo módulo `Sourcing` respeita as
fronteiras; `HttpErrorMappingCompletenessTest` confirma que as 2 novas exceções têm status mapeado.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 114, Failures: 0, Errors: 0,
Skipped: 0` (108 baseline + 6 da fatia). Portões verdes: ArchUnit, Spring Modulith (10 módulos),
Spotless, Checkstyle, completude do `HttpErrorMapping`.

## Cobertura — o que NÃO está coberto (e por quê)

- **Ramo INTEGRATED do Quote** (`composeIntegrated`, colunas nulas, V10): Slice 8b.
- **ACL de entrada** (webhook assinado, idempotência, tradução do payload externo,
  `IntegratedQuoteCreated`, health do conector): Slice 8c.
- **Tela Angular** de Sourcing: não há jornada de tela nesta fatia (registro manual é endpoint de
  rastreio do operador; o valor de tela da fase é a cotação INTEGRADA criada pela ACL).

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=SourcedOfferTest               # unit
cd backend && ./mvnw verify -Dtest=SourcingIntegrationTest       # integração (Docker up)
cd backend && ./mvnw verify                                      # tudo + portões
```
