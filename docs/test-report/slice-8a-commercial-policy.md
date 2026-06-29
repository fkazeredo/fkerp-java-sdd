# Caderno de testes — Slice 8a: CommercialPolicy (parâmetros governados + precedência) (SPEC-0014)

## Escopo

Gradua o stub de markup da Fase 1 (`SystemDefaultMarkupProvider`) no **motor real de parâmetros
governados** no módulo `com.fksoft.domain.commercialpolicy` (o 12º Modulith, já existente — **não**
cria módulo novo). Entrega: o agregado `ParameterRule` por **camada** (DIRECTIVE > PROMOTION >
CONTRACT > POLICY > SYSTEM_DEFAULT) × **escopo** (conta/produto/canal) × **vigência**; o motor
`resolve(key, scope) → {value, provenance}` (BR2/BR3, DL-0037); a definição auditável de regras e
**diretivas** em runtime (BR5/BR7, Q8 = DL-0038); o seed dos `SYSTEM_DEFAULT` (BR4, DL-0039); a
**graduação** do `MarkupProvider` preservando o contrato (DL-0040). Cobre os Acceptance Criteria:
resolução com proveniência, diretiva passa a vencer e fica auditada, e o Quoting compõe usando esse
markup (não mais o default fixo).

## Casos de teste

### Unitário/domínio — `ParameterResolverTest` (a prova de precedência exigida pela fase, fixtures exatas)
| Caso | Verifica | Regra |
|---|---|---|
| `fallsBackToSystemDefaultWhenNoOtherRuleApplies` | só há SYSTEM_DEFAULT → ele vence (resolução nunca vazia) | BR4 |
| `higherLayerWinsEvenWithLessSpecificScope` | DIRECTIVE global (espec. 0) bate POLICY de conta (espec. 1) | BR2/BR3 |
| `promotionBeatsPolicyEvenWhenPolicyIsMoreSpecific` | PROMOTION global bate POLICY de produto+conta (camada manda) | BR3 |
| `moreSpecificScopeWinsWithinTheSameLayer` | dentro de POLICY: produto+conta (2) > conta (1) > global (0) | BR3 |
| `doesNotMatchRuleScopedToAnotherAccount` | regra de outra conta não casa → cai no SYSTEM_DEFAULT | BR3 (matcher) |
| `ignoresRulesNotInEffectOnTheResolutionDate` | PROMOTION expirada (jan) ignorada em jun → SYSTEM_DEFAULT | BR2 (vigência) |
| `tieBreakIsDeterministic_newerValidFromWins` | mesma camada+especificidade: `validFrom` mais novo vence; **ordem da lista não muda o vencedor** | BR8 / DL-0037 (desempate determinístico) |
| `resolvesEmptyWhenNothingActiveOrMatching` | nada ativo/casando → vazio (vira 404 no serviço) | BR4 |

### Unitário/domínio — `ParameterValueObjectsTest` (value objects)
| Caso | Verifica | Regra |
|---|---|---|
| `parameterKeyParsesUpperSnakeCaseAndRejectsMalformed` | `markup_pct`→`MARKUP_PCT`; `bad-key`/vazio rejeitados | BR1 / validação |
| `scopeSpecificityCountsFixedDimensions` | especificidade = nº de dimensões não-nulas (0..3) | DL-0037 |
| `globalRuleMatchesAnyQueryButSpecificRuleOnlyMatchesItsScope` | curinga (nulo) casa qualquer; dimensão fixa só casa igual | BR3 |
| `blankScopeDimensionsAreNormalizedToWildcard` | string em branco vira curinga (nula) | robustez do matcher |
| `valueTypeValidatesItsText` | PERCENT/MONEY/NUMBER decimais; BOOL true/false; inválidos recusados | Validation Rules |
| `markupDecisionFromResolvedCarriesWinningLayerAsSource` | `MarkupDecision.from` → `source` = camada vencedora (PROMOTION) | DL-0040 |

### Integração (Testcontainers/Postgres) — `CommercialPolicyIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `resolvesTheSeededSystemDefaultWithProvenance` | `GET /resolve?key=MARKUP_PCT` → value `0`, type PERCENT, proveniência `SYSTEM_DEFAULT`/`system-seed` | AC1 (valor + proveniência) / BR4 (seed) |
| `aDirectiveImmediatelyWinsForItsScopeAndIsAudited` | antes: conta → SYSTEM_DEFAULT; `POST /directives` (201, justificativa persistida); depois: conta → DIRECTIVE `0.08` **imediatamente**; outra conta inalterada | AC2 (diretiva vence + auditada + reflete já) / BR5 |
| `returns404ForAKeyWithoutASystemDefault` | key sem default → 404 `policy.parameter.unknown` | Error Behavior / BR4 |
| `issuingADirectiveWithoutTheDirectorRoleIsForbidden` | `defineRule` de DIRECTIVE com papéis `{ROLE_DEV}` → `PolicyDirectiveForbiddenException` (403) | BR5/BR7 / DL-0038 |

### Integração (Testcontainers) — `MarkupGraduationIntegrationTest` (regressão de fronteira — graduação do stub)
| Caso | Verifica | Tests Required |
|---|---|---|
| `aDirectiveMarkupFlowsIntoAFreshlyComposedQuote` | diretiva MARKUP_PCT `0.10` p/ a conta → Quote composta: `markup.source=DIRECTIVE`, pct `0.10`, amount `270.00`, sugerido `2970.00` | "markup do Quoting passa a vir do motor (proveniência ≠ SYSTEM_DEFAULT)" |
| `withoutAnyRuleTheQuoteStillUsesTheSystemDefaultMarkup` | sem regra → Quote com `markup.source=SYSTEM_DEFAULT`, pct `0`, sugerido `2700.00` (**back-compat**) | back-compat (stub graduado sem quebrar) |
| `QuoteIntegrationTest.composesTheOrlandoCarSaleWithFrozenProvenance` (herdado) | continua afirmando `markup.source == SYSTEM_DEFAULT` sem regra → **passa intacto** | regressão de não-quebra do Quoting |

### Arquitetura / portões
- **Spring Modulith** `verify()` **acíclico** com os 12 módulos (commercialpolicy graduado no lugar;
  quoting já dependia da porta `MarkupProvider` — sem ciclo novo). **Verde.**
- **ArchUnit** (10 regras) verde; `ParameterRule`/`ParameterResolver`/repositório em `internal`
  (entidade não sai do módulo).
- `HttpErrorMappingCompletenessTest`: as 3 novas exceções (`policy.parameter.unknown` 404,
  `policy.rule.invalid` 400, `policy.directive.forbidden` 403) mapeadas. **Verde.**
- i18n pt-BR + fallback en para as 3 chaves. OpenAPI atualizada (descrição + versão 0.9.0).
- Spotless/Checkstyle: **0 violações**.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 239, Failures: 0, Errors: 0,
Skipped: 0` (baseline 219 + 8 `ParameterResolverTest` + 6 `ParameterValueObjectsTest` + 4
`CommercialPolicyIntegrationTest` + 2 `MarkupGraduationIntegrationTest` = +20). Migração **V18**
aplicada e validada (Postgres real, via Testcontainers). 0 Checkstyle.

## Cobertura — o que NÃO está coberto (e por quê)

- **Consumo de `AGENT_COMMISSION_PCT`/drift/tolerância** por Commissioning/Exchange/Reconciliation:
  fora de escopo (Q5 é da SPEC-0004; Open-Host só **expõe** — DL-0039). O motor está pronto, sem
  consumidor novo (simulation-and-mocking.md).
- **`POST /rules` (POLICY/PROMOTION/CONTRACT) via HTTP**: coberto indiretamente (mesmo `defineRule`
  do `/directives`, que é testado fim-a-fim incl. 201/403); o caminho de regra não-diretiva tem o
  fluxo de autorização exercitado pelo teste de 403 no serviço. Tela Angular: backend-first (follow-up).
- **IdP real / papéis reais**: `DevStubUserContextProvider` é stub (SPEC-0024); o 403 é provado no
  serviço com papéis injetados.

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply && ./mvnw verify     # tudo (Docker no ar p/ Testcontainers)
# só o motor de precedência (rápido, sem DB):
cd backend && ./mvnw -Dtest=ParameterResolverTest,ParameterValueObjectsTest test
# só as integrações desta fatia:
cd backend && ./mvnw -Dtest=CommercialPolicyIntegrationTest,MarkupGraduationIntegrationTest test
```
