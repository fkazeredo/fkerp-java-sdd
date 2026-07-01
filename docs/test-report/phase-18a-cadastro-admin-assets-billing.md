# Caderno de testes — Fase 18a (módulo `cadastro` + enums→cadastro Admin/Assets/Billing)

- **Spec:** SPEC-0031 (nova). Decisões: ADR-0019, DL-0115.
- **Release:** 0.29.0.

## Escopo (Acceptance Criteria cobertos)

- **AC1** — `GET /api/cadastro/types` lista os tipos; `GET /api/cadastro/items?type=…` lista os itens
  semeados (ativos primeiro).
- **AC2** — com `ROLE_POLICY_ADMIN`, `POST`/`PUT`/`DELETE` criam/editam/desativam; sem o papel → 403.
- **AC3** — registrar com um `code` conhecido retorna o **mesmo JSON** (contrato inalterado); um `code`
  desconhecido/inativo é **rejeitado (422)**.
- **AC4** — despesa `UTILITY` continua gerando `UTILITY_EXPENSE`; `SIMPLES_NACIONAL` sem retenções
  (ramificação preservada).
- **AC5** — a tela "Cadastros" (papel admin) lista tipos, lista/edita/ativa/desativa; sem o papel, some
  da navegação e mostra o estado de permissão.

## Casos de teste por tipo

### Unitário (backend)

| Caso | Verifica | AC/Regra |
|---|---|---|
| `CadastroItemTest.createNormalizesCodeToUpperCaseAndIsBornActive` | code normalizado (upper), nasce ativo | BR1 |
| `CadastroItemTest.createRejectsMissingMandatoryData` | type/code/label obrigatórios | BR1 |
| `CadastroItemTest.updateChangesLabelActiveAndOrderButNotCode` | code imutável; label/active/order editáveis | BR2 |
| `CadastroItemTest.deactivateIsIdempotentSoftDelete` | desativar é soft e idempotente | BR2 |
| `CadastroItemTest.updateRejectsBlankLabel` | rótulo em branco rejeitado | BR2 |
| `AdminExpenseCodesTest.eachKnownCodeMapsToTheExpectedEntryType` | kind→EntryType preservado | AC4/DL-0085 |
| `AdminExpenseCodesTest.anUnknownCodeFallsBackToOtherExpense` | code novo → OTHER_EXPENSE (seam) | DL-0115 |
| `TaxRegimeStrategyTest.*` | ISS/retenções por regime (código `SIMPLES_NACIONAL`) | AC4/DL-0044 |
| `CommissionInvoiceTest.*` | draft/emissão com o `code` de regime | invariante |

### Integração (Testcontainers/Postgres)

| Caso | Verifica | AC/Regra |
|---|---|---|
| `CadastroApiIntegrationTest.listsTheConvertibleTypes` | `GET /types` | AC1 |
| `CadastroApiIntegrationTest.listsSeededItemsOfATypeActiveFirst` | itens semeados (V33), ativos | AC1 |
| `CadastroApiIntegrationTest.policyAdminCanCreateUpdateAndDeactivateAnItem` | CRUD com o papel | AC2 |
| `CadastroApiIntegrationTest.creatingAnItemWithoutThePolicyAdminRoleIsForbidden` | 403 sem o papel | AC2 |
| `CadastroApiIntegrationTest.duplicateCodeForATypeIsRejected` | unique (type, code) → 409 | BR1 |
| `CadastroConversionInvariantIntegrationTest.supplierTypeCodeRoundTripsTheSameWireValue` | JSON inalterado | AC3 |
| `CadastroConversionInvariantIntegrationTest.anUnknownSupplierTypeCodeIsRejected` | code inválido → 422 | AC3 |
| `CadastroConversionInvariantIntegrationTest.anInactiveAssetTypeCodeIsRejected` | code inativo → 422 | AC3/BR3 |
| `CadastroConversionInvariantIntegrationTest.aValidActiveAssetTypeCodeRoundTrips` | code válido round-trip | AC3 |
| `AdminExpenseIntegrationTest.*` (atualizado) | UTILITY→UTILITY_EXPENSE etc. via `code` | AC4 |
| `AdminSupplierContractIntegrationTest.*` / `AdminContractExpiryIntegrationTest.*` (atualizados) | supplier/contract com `code` | AC3/AC4 |
| `AssetApiIntegrationTest.*` / `AssetLicenseExpiryIntegrationTest.*` (atualizados) | asset com `code`; SOFTWARE_LICENSE exige expiração | AC3/AC4 |

### Unitário (frontend, Vitest)

| Caso | Verifica | AC |
|---|---|---|
| `CadastroService.*` (5 casos) | GET types/items, POST/PUT/DELETE nos endpoints certos | AC1/AC2 |
| `CadastroPage.loads types and the first type items` | loading→success | AC5 |
| `CadastroPage.shows the empty state` | estado vazio | AC5 |
| `CadastroPage.renders the permission state on a 403` | estado de erro/permissão | AC5 |
| `CadastroPage.creates a new item and reloads` | criar recarrega | AC5 |
| `CadastroPage.surfaces a create error by its code` | 403 na escrita | AC2/AC5 |
| `CadastroPage.deactivates/reactivates via toggle` | ativar/desativar | AC5 |
| `CadastroPage.saves an edited item` | salvar edição | AC5 |

### E2E (Playwright — autorado)

| Caso | Verifica | AC |
|---|---|---|
| `cadastro.spec.ts › a policy-admin can open Cadastros, add a code and edit an item` | jornada admin | AC5 |
| `cadastro.spec.ts › a non-admin is denied (403) when creating a cadastro item` | permissão | AC2 |
| `cadastro.spec.ts › the converted supplier-type code round-trips the same wire value` | invariante | AC3 |

## Resultado

- **Backend `./mvnw verify`: BUILD SUCCESS — 495 testes**, 0 falhas (480 → 495, +15). ArchUnit,
  Spring Modulith (23 módulos, grafo acíclico), Spotless, Checkstyle (0), JaCoCo (≥ 0,80) — **nenhum
  portão afrouxado**. Nenhuma mudança de contrato de fio.
- **Frontend: lint 0, 278 testes** (51 arquivos) Vitest, cobertura acima dos pisos (stmts 73,9% /
  branches 56,5% / funcs 51,4% / lines 79,2%), build de produção OK.
- **E2E:** `cadastro.spec.ts` (3 testes) **autorado e compila** (`npx playwright test --list`);
  **não executado** neste ambiente (o build da imagem do backend em contêiner precisa de rede de
  artefatos Maven — limite do sandbox; host verde).

## Cobertura — o que NÃO está coberto e por quê

- As telas **Admin/Assets/Billing** continuam exibindo o **código** do item (não o rótulo do
  cadastro) — o invariante é o `code`, e renderizar o código mantém a Regra Zero; buscar o rótulo em
  cada linha seria trabalho extra sem valor claro nesta fatia. (Seam registrado; pode entrar em 18+.)
- **Trava de obrigatoriedade** (impedir desativar o último ativo de um tipo) — Open Question da
  SPEC-0031, adiada.
- Os **demais grupos de enums** (18b–18d) ainda são enums — convertidos nas fatias seguintes.

## Como reproduzir

```bash
# Backend (Docker no ar para Testcontainers)
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run lint && npm run test:coverage && npm run build

# E2E (lista/compila)
cd frontend && npx playwright test --list e2e/cadastro.spec.ts
```
