# Caderno de testes — Slice 4: Quoting (SPEC-0005, keystone)

## Escopo

Composição da cotação MANUAL (preço-base + câmbio congelado + comissão de duas pontas + markup),
`suggestedAmount` × `appliedAmount`, e `OverrideRecord {quem, quando, de→para, motivo}` a cada
divergência — tudo com **proveniência congelada** (BR4/BR5). Consome as fachadas de Accounts,
Exchange, Commissioning e o `MarkupProvider` stub de CommercialPolicy. Fórmula de preço conforme
[DL-0009](../decision-log/DL-0009-quoting-formula-de-preco.md) (base BRL + markup default 0).

## Casos de teste

### Unitário/domínio — `Quote` (`QuoteAggregateTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `chainsOverridesAndKeepsSuggestionImmutable` | 2 overrides encadeados (2700→2650→2600), `suggested` imutável | BR5/BR6/BR8 |
| `rejectsEmptyReason` | motivo vazio → `quoting.override.reason-required` | BR6 |
| `rejectsCurrencyMismatch` | moeda ≠ sugerida → `quoting.override.currency-mismatch` | BR7 |

### Integração (Testcontainers, fachadas reais) — `QuoteIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `composesTheOrlandoCarSaleWithFrozenProvenance` | USD 500 × 5,40 = 2.700 BRL; 405/270/135; markup 0; `suggested=applied=2700`; `provenance.rateId` presente | AC1 |
| `appliesOverrideWithReasonAndRejectsEmptyReason` | override com motivo registra record e atualiza applied; sem motivo → 400 | AC2 |
| `returns422WhenNoRateForPair` | sem taxa → 422 `quoting.rate.missing` | AC3 |
| `returns404WhenAccountDoesNotExist` | conta inexistente → 404 `quoting.account.not-found` | BR2 |
| `freezesProvenanceSoLaterRateChangesDoNotAlterTheQuote` | fixar 5,55 após compor não muda a cotação (segue 5,40 / 2.700) | AC4 (regressão) |

### Arquitetura
Spring Modulith `verify()` com o módulo `quoting` (`@ApplicationModule`): a colaboração com
`accounts`/`exchange`/`commissioning`/`commercialpolicy` se dá **só por fachadas** (portas no
pacote-base); o `internal` (entities/repository) é privado. Introduz `commercialpolicy` como
**stub rastreável** (markup `SYSTEM_DEFAULT`, graduado pela SPEC-0014).

## Resultado

`./mvnw verify` → **BUILD SUCCESS**. `Tests run: 62` (Slice 3: 54 → +8). Spotless clean, Checkstyle 0.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** (composição: sugerido vs. aplicado + override com motivo) — **pendente** (leva de
  frontend da Fase 1).
- Origem `INTEGRATED`, motor de precedência de CommercialPolicy, promoções, tributos — fora de escopo
  (SPEC-0009 / SPEC-0014).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=QuoteAggregateTest
./mvnw test -Dtest=QuoteIntegrationTest
```
