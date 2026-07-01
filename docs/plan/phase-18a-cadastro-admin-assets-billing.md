# Plano — Fase 18a (módulo `cadastro` + enums→cadastro Admin/Assets/Billing)

- **Spec:** SPEC-0031 (nova). Decisões: ADR-0019, DL-0115. Release 0.29.0.
- **Invariante:** valor persistido vira `code` (String) = nome do enum ⇒ **JSON de contrato
  inalterado**; ramificação preservada por constantes `*Codes`; código novo funciona como dado puro.

## Fatia única (18a), em ordem de dependência

1. **Docs primeiro** (RUN-PHASE): DL-0115, SPEC-0031, ADR-0019.
2. **Módulo `cadastro` (23º Modulith, folha):** entidade `CadastroItem` (`cadastro_item`, unique
   `(type, code)`), catálogo `CadastroType`, repositório, `CadastroService` (CRUD; `code` imutável),
   porta pública `CadastroValidator`, exceções, view/commands, `package-info`. Delivery:
   `CadastroController` + DTOs. `HttpErrorMapping` + i18n (pt-BR/en). `SecurityConfig`:
   `POST/PUT/DELETE /api/cadastro/**` → `ROLE_POLICY_ADMIN`.
3. **Migração V33:** cria `cadastro_item` + semeia os valores dos enums convertidos (code=nome do
   enum, label pt-BR), idempotente.
4. **Conversão Admin** (`AdminExpenseKind`/`AdminRecurrence`/`AdminSupplierType`): campos→`String
   code`, `AdminExpenseCodes.entryTypeFor` (DL-0085), validação via porta; DTOs/views/eventos/repos/
   serviço/controller; deletar enums.
5. **Conversão Assets** (`AssetType`): idem, `AssetCodes.isSoftwareLicense` mantém a regra de
   expiração; JPQL usa literal `'SOFTWARE_LICENSE'`.
6. **Conversão Billing** (`WithholdingKind`/`TaxRegime`): `TaxRegimeCodes`/`WithholdingKindCodes`;
   `TaxAssessment`/`Withholding`/`CommissionInvoice(View)`/`BillingTaxRegimeConfig`/estratégia/codec.
7. **Testes backend:** unit (`CadastroItem`, `AdminExpenseCodes`), integração (CRUD + gate 403 +
   round-trip do JSON + rejeição de código inválido/inativo); atualizar os testes que usavam os enums
   para `code`. `./mvnw verify` verde.
8. **Frontend:** feature `cadastro` (models/service/page + specs), rota + nav gated por
   `ROLE_POLICY_ADMIN`, i18n pt-BR/en. lint/test/build verdes.
9. **E2E:** `cadastro.spec.ts` (admin edita; não-admin 403; round-trip). Compila via `--list`.
10. **Versão 0.29.0** (pom + OpenAPI). **Docs bilíngues:** MANUAL pt-BR+en, release note pt-BR +
    CHANGELOG en, test-report, decision-log INDEX.
11. **Publicar** (gitflow worktree-safe): push `feature/18a-integration:develop`, tag `0.29.0`, merge
    `--no-ff` em main.

## Não-metas (fora de 18a)

- Converter os demais grupos de enums (18b–18d). Trava de obrigatoriedade por tipo (Open Question).
- Exibir o rótulo do cadastro nas telas Admin/Assets/Billing (hoje exibem o código — Regra Zero).
