# Caderno de testes — Slice 8g-1 (Portfolio: marcas + contratos + alerta de expiração)

- **Spec:** SPEC-0020 (Portfolio — representação)
- **Acceptance Criteria cobertos:** registrar a marca e o contrato (com documento no cofre por valor);
  contrato a vencer gera alerta de governança; `./mvnw verify` verde.
- **Regras cobertas:** BR1 (RepresentedBrand: brandRef único, displayName, status), BR2
  (RepresentationContract: vigência + documento Compliance por valor; cobertura como leitura —
  DL-0061), BR5 (mudança auditada; expiração publica `RepresentationExpiring` — DL-0063), BR6
  (Portfolio não precifica).
- **Decisões:** DL-0060 (módulo separado, 17º), DL-0061 (vender sem contrato vigente apenas alerta),
  DL-0063 (alerta de expiração por relógio controlado, 30d, idempotente).

## Casos de teste

### Unitário — `RepresentationContractTest` (domínio, `internal`)
| Caso | Verifica | Regra |
|---|---|---|
| `registersWithAValidWindowAndTerms` | registra com vigência e termos (jsonb) | BR2 |
| `rejectsAMissingValidFrom` | validFrom obrigatório → exceção | BR2 |
| `rejectsValidUntilBeforeValidFrom` | vigência incoerente → exceção | BR2 |
| `inForceCheckHonorsTheWindow` | em vigor só dentro de [from, until] | BR2/DL-0061 |
| `openEndedContractIsInForceForeverAfterStart` | sem validUntil = aberto | BR2 |
| `signalsExpiryOnceWhenWithinTheWarningWindowAndIsIdempotent` | sinaliza 1x na janela de 30d; 2ª chamada não | BR5/DL-0063 |
| `doesNotSignalWhenStillFarFromExpiry` | fora da janela (60d) não sinaliza | DL-0063 |
| `signalsAnAlreadyExpiredContractToo` | contrato já vencido sinaliza | DL-0063 |
| `neverSignalsAnOpenEndedContract` | sem validUntil nunca sinaliza | DL-0063 |

### Integração (Testcontainers/Postgres) — `BrandAndContractIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `registeringABrandStartsItActiveAndPublishesTheEvent` | marca nasce ACTIVE; publica `BrandRepresented` | BR1 |
| `duplicateBrandRefIsATranslatedConflict` | brandRef duplicado → `BrandDuplicateException` (409), sem constraint crua; 1 linha | BR1 |
| `fetchingAMissingBrandIsNotFound` | id inexistente → `BrandNotFoundException` (404) | BR1 |
| `listingFiltersByStatus` | lista por ACTIVE/INACTIVE após desativar | BR1/BR5 |
| `registeringAContractLinksTheComplianceDocumentByValueAndPublishesTheEvent` | documentId por valor; termos; `RepresentationContractRegistered` | BR2 |
| `registeringAContractForAMissingBrandIsNotFound` | contrato para marca inexistente → 404 | BR2 |
| `contractCoverageIsAReadModelAlertNotABlock` | cobertura true/false; marca sem contrato = não coberta, **sem exceção** | BR2/DL-0061 |
| `expirySweepPublishesRepresentationExpiringOnceForADueContract` | job publica `RepresentationExpiring` 1x; 2º sweep não republica | BR5/DL-0063 |
| `expirySweepIgnoresContractsFarFromExpiry` | contrato a 90d não é sinalizado | DL-0063 |

### Arquitetura (gates)
| Caso | Verifica |
|---|---|
| `ModularityTests.verifiesModularStructure` | Spring Modulith aceita o **17º módulo** `portfolio`; grafo acíclico |
| `ArchitectureTest` (todas as regras) | domínio não depende de application/infra; sem setters em @Entity; sem *Impl; injeção por construtor |
| `ArchitectureRulesHaveTeethTest` | as regras realmente quebram ao plantar violação |
| `HttpErrorMappingCompletenessTest` | toda `DomainException` (inclui as novas de Portfolio) está mapeada |

## Resultado

- **`./mvnw -o test -Dtest=RepresentationContractTest,BrandAndContractIntegrationTest,ModularityTests,
  ArchitectureTest,ArchitectureRulesHaveTeethTest,HttpErrorMappingCompletenessTest` → verde** (0 falhas).
- **Spotless** `check` verde (após `apply`); **Checkstyle** 0 violações.
- Migração `V25__create_portfolio.sql` aplicada (Flyway "now at version v25"); schema sobe limpo.
- A contagem total do `./mvnw verify` consolidado é reportada na fatia 8g-2 (suíte completa).

## Cobertura

- **Coberto:** ciclo de marca (registrar/listar/desativar), contrato (registrar/listar/cobertura),
  expiração (job de relógio controlado idempotente), unicidade de brandRef traduzida, eventos
  publicados.
- **NÃO coberto nesta fatia (entra em 8g-2):** `BrandGoal` e a projeção realizado vs meta a partir de
  eventos de venda (BR3/BR4); tela Angular (Portfolio é módulo de referência/back-office, sem jornada
  de tela nesta fase — consistente com fatias 8c–8f).

## Como reproduzir

```bash
cd backend
./mvnw -o test -Dtest='RepresentationContractTest,BrandAndContractIntegrationTest'   # unit + integração
./mvnw -o test -Dtest='ModularityTests,ArchitectureTest,HttpErrorMappingCompletenessTest'  # gates
./mvnw -o spotless:check checkstyle:check                                            # formato/estilo
```
