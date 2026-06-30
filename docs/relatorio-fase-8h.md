# Relatório — Fase 8h (Assets · SPEC-0021) · Release 0.16.0

- **Data:** 2026-06-29
- **Branch de integração:** `feature/8h-integration` (base `origin/develop` @ e3b208b)
- **Release/tag:** `0.16.0`

## Fatias entregues

| Fatia | Entrega | Resultado |
|---|---|---|
| **8h-1** Registro + ciclo de vida | 18º módulo Modulith `assets` (folha): agregado `Asset` (EQUIPMENT/SOFTWARE_LICENSE/OTHER), nasce ACTIVE, baixa auditada e terminal; licença exige `expiresAt`; vínculos Compliance/Finance por valor; publica `AssetRegistered`; API REST (POST/GET/list/retire); V26; i18n; OpenAPI; HttpErrorMapping | ✅ verde (383 testes) |
| **8h-2** Alerta de licença a vencer | `AssetLicenseExpiryScheduler` (infra.jobs) relógio controlado 30d publica `AssetLicenseExpiring` uma vez por licença (idempotente por `expiry_signaled_at`); listagem `?expiringWithinDays=N`; reusa V26 | ✅ verde (388 testes) |

## Arquivos criados/alterados (alto nível)

- **Domínio (novo módulo `com.fksoft.domain.assets`):** `package-info.java` (`@ApplicationModule`),
  `AssetType`, `AssetStatus`, `AssetView`, `RegisterAssetCommand`, `AssetService`, eventos
  `AssetRegistered`/`AssetLicenseExpiring`, exceções `AssetNotFoundException`/`AssetInvalidException`/
  `LicenseExpiryRequiredException`/`AssetAlreadyRetiredException`; `internal/Asset` (agregado) +
  `internal/AssetRepository`.
- **Delivery:** `application/api/AssetsController`; DTOs `RegisterAssetRequest`, `RetireAssetRequest`.
- **Infra:** `infra/jobs/AssetLicenseExpiryScheduler`; `infra/web/HttpErrorMapping` (+4 mapeamentos);
  `infra/openapi/OpenApiConfig` (descrição + versão 0.16.0).
- **Recursos:** `db/migration/V26__create_assets.sql`; `messages_pt_BR.properties` +
  `messages.properties` (4 chaves Assets).
- **Testes:** `domain/assets/internal/AssetTest` (5 unit); `assets/AssetApiIntegrationTest` (4);
  `assets/AssetLicenseExpiryIntegrationTest` (5).
- **Build:** `backend/pom.xml` versão 0.16.0.

## Migração

- **V26__create_assets.sql** — tabela `assets` (custo `acquisition_cost`+`currency`; `document_id`/
  `finance_entry_id` valores; `expiry_signaled_at`; auditoria de baixa) + índices `ix_assets_type_status`
  e `ix_assets_license_expiry` (parcial). Idempotente; nenhuma FK cross-contexto. Confirmada na execução:
  "Successfully applied 26 migrations ... now at version v26".

## OpenAPI

- Novos endpoints sob `/api/assets` documentados; descrição da API atualizada; versão **0.16.0**.

## Specs / ADRs

- **SPEC-0021** atualizada: Open Questions (Q2; depreciação) movidas para *Business Rules* como
  ASSUMIDO (BR6–BR10 → DL-0064..0068). Nenhum ADR novo (decisões registradas no decision-log).

## Decisões (decision-log)

| DL | Decisão | Conf. | Rev. |
|---|---|---|---|
| [DL-0064](decision-log/DL-0064-assets-separate-context-lean-registry.md) | Assets é contexto separado (Q2: dois contextos); registro enxuto; 18º módulo | Alta | Moderada |
| [DL-0065](decision-log/DL-0065-assets-no-depreciation-buy-vs-build-seam.md) | Sem depreciação/gestão plena; registro + seam comprar-vs-construir | Alta | Moderada |
| [DL-0066](decision-log/DL-0066-assets-license-expiry-controlled-clock-job-30d.md) | Alerta de licença por relógio controlado, 30d, idempotente | Média | Barata |
| [DL-0067](decision-log/DL-0067-assets-publishes-events-leaf-no-consumers-now.md) | Assets folha: publica eventos, não fia consumidores Finance/Intelligence | Média | Barata |
| [DL-0068](decision-log/DL-0068-assets-retire-audit-inline-and-status-machine.md) | Baixa auditada inline; ACTIVE→RETIRED terminal; re-baixar → 409 | Média | Barata |

**Nenhuma decisão é Confiança=Baixa + Reversibilidade=Cara.** A Q2 foi resolvida pela recomendação do
arquiteto (dois contextos) e o restante segue padrões já validados (relógio controlado; módulo-folha).

## Testes por tipo e resultado

- **Unitário (domínio):** `AssetTest` (5) — BR1 (licença exige `expiresAt`), RETIRED terminal,
  idempotência do sinal de expiração. ✅
- **Integração (Testcontainers/Postgres):** `AssetApiIntegrationTest` (4) — jornada REST + sad paths
  (400/404/409); `AssetLicenseExpiryIntegrationTest` (5) — sweep idempotente, `?expiringWithinDays`,
  eventos. ✅
- **Arquitetura:** ArchUnit 14 regras; Spring Modulith acíclico com o 18º módulo; HttpErrorMapping
  completo. ✅
- **Smoke:** `/api/system/health` intacto. ✅

### Saída do `./mvnw verify` (integração)

```
Tests run: 388, Failures: 0, Errors: 0, Skipped: 0
You have 0 Checkstyle violations.
BUILD SUCCESS
```

(eram 374 na 0.15.0; +14 de Assets.)

## Riscos / o que fica para a próxima fase

- **Custo automático no Finance e insight de custo fixo no DSS:** seam publicado (eventos), não fiado
  (DL-0067) — adicionar listener no consumidor quando a política de custo de patrimônio existir.
- **Depreciação/manutenção/estoque:** comprar sistema dedicado (DL-0065); este módulo é o registro/seam.
- **Tela Angular do Assets:** fora do escopo da SPEC-0021 (back-office, como 8c–8g); entra na Fase 10
  (UX profissional).
- **Horizonte de 30 dias e antecedência** são configuráveis (`assets.license.horizon-days`) — confirmar
  com a governança/TI se o número real difere.
