# Relatório da Fase 8j — Platform (custódia e-CNPJ + governança de jobs + auditoria de sistema)

- **Spec:** SPEC-0023 · **Release:** 0.18.0 · **Migração:** V28 · **Base:** `origin/develop` @46b6db4
- **Resultado:** `./mvnw verify` **verde — 434 testes, 0 falhas**; ArchUnit 15 regras; Spring Modulith
  acíclico (20 módulos); Spotless 0; Checkstyle 0.

## Fatias entregues

| Fatia | O que entrega |
|---|---|
| **8j-1** Custódia do e-CNPJ | Novo módulo `domain.platform` (20º Modulith). Material do certificado **cifrado at-rest** (AES-256-GCM, chave fora do banco, porta `SecretCipher`); `GET /api/platform/certificate/status` **só metadados**; `POST /api/platform/certificate` (cifra na entrada); `CertificateExpiring` por relógio controlado (BR5); porta `CertificateSigner` do Platform, stub do Billing **delega** (DL-0078). |
| **8j-2** Governança de jobs | `ScheduledJob`/`JobRun` + `runWithGovernance` (idempotência por `(job,window)`, falha→**FAILED nunca sucesso**, BR3); porta `JobLock` + **advisory lock no Postgres** (um por vez→409); `GovernedJobs` liga os 5 schedulers existentes + novo `RepresentationExpiryScheduler`; `/api/platform/jobs`, `/runs`, `/{name}/trigger`(202); catálogo seedado V28. |
| **8j-3** Auditoria de sistema | `SystemAuditEntry` **append-only** consolidando eventos de segurança/integração/jobs por listener in-process + fachada `record(...)` (DL-0077); `GET /api/platform/audit` filtrável/paginado; detalhe **só metadados** (nunca segredo). |
| **8j-4** Docs + release | MANUAL pt-BR + en-US (seção Platform + glossário + histórico, versão 0.18.0); release note `0.18.0.md` + CHANGELOG.en-US; OpenAPI 0.18.0; caderno de testes por fatia + INDEX; SPEC-0023 Open Questions → Business Rules. |

## Arquivos criados/alterados (alto nível)

- **Domínio novo** `com.fksoft.domain.platform` (+ `internal`): `PlatformCertificate`, `CertificateCustodyService`,
  `CertificateStatus/View/Fingerprint/Expiring`, `SecretCipher`/`CertificateSigner`/`ImportCertificateCommand`,
  `CertificateNotFound/Unavailable`; `ScheduledJob`/`JobRun`/`JobRunRecorder`/`PlatformJobService`,
  `JobStatus/FailureClass/Outcome/Run(Started|Finished)/Lock`, `JobNotFound/Locked`;
  `SystemAuditEntry`/`SystemAuditService`/`AuditType/View`, `PlatformAuditListener`; `package-info` (@ApplicationModule).
- **Infra** `com.fksoft.infra.platform`: `AesGcmSecretCipher`, `CustodyCertificateSigner`, `PostgresAdvisoryJobLock`.
  `infra.jobs`: `GovernedJobs`, `CertificateExpiryScheduler`, `RepresentationExpiryScheduler`; 5 schedulers
  religados (SLA, licença, retenção, crawler, +representação). `infra.integration.nfse.StubECnpjCertificateSigner`
  delega ao Platform. `infra.web.HttpErrorMapping` +4 mapeamentos. `infra.openapi` 0.18.0.
- **Delivery** `application.api`: `PlatformCertificateController`, `PlatformJobController`, `PlatformAuditController`
  + DTO `ImportCertificateRequest`.
- **Recursos:** `db/migration/V28__create_platform.sql`; `messages*.properties` (+8 chaves); `application.yml`
  (config platform/portfolio).
- **Testes:** `AesGcmSecretCipherTest`, `CertificateCustodyIntegrationTest`, `JobGovernanceIntegrationTest`,
  `PlatformJobApiIntegrationTest`, `SystemAuditIntegrationTest`; `ArchitectureTest`/`...HaveTeethTest` (+regra +fixture).
- **Docs:** plano `phase-8j-platform.md`; DL-0073..0078 + INDEX; specs/0023 (Business Rules); test-report 8j-1/2/3 + INDEX;
  release 0.18.0 + CHANGELOG.en-US; MANUAL pt-BR + en-US; este relatório.

## Testes por tipo

- **Unitário:** cipher AES-GCM (round-trip, IV aleatório, detecção de adulteração, chave 32 bytes) — 5.
- **Integração (Testcontainers/Postgres):** custódia (status só metadados, material cifrado, expiração,
  regressão de segurança) — 4; jobs (sucesso/SKIPPED idempotente/FAILED não-sucesso/lock concorrente/histórico) — 5;
  API de jobs (catálogo/trigger 202/404) — 3; auditoria (consolidação job+certificado/append-only/segurança/filtro) — 4.
- **Arquitetura:** ArchUnit 15 regras (nova "Platform sem regra de domínio" BR6, com teeth test); Modulith acíclico.
- **Resultado `./mvnw verify`:** BUILD SUCCESS, **Tests run: 434, Failures: 0, Errors: 0**.

## Migração / OpenAPI

- **V28** (idempotente, nunca editada após aplicada): `platform_certificates`, `scheduled_jobs` (+seed 6 jobs),
  `job_runs` (UNIQUE parcial de idempotência), `system_audit` (append-only). Sem FK cross-contexto.
- **OpenAPI 0.18.0:** +6 endpoints `/api/platform/*`; nenhum retorna material de certificado.

## Decisões (decision-log)

- [DL-0073](decision-log/DL-0073-platform-new-domain-module.md) módulo `platform` (Alta/Moderada).
- [DL-0074](decision-log/DL-0074-platform-certificate-encryption-at-rest.md) **custódia AES-256-GCM, chave fora
  do banco (Confiança=Baixa / Reversibilidade=Cara — DESTAQUE)**.
- [DL-0075](decision-log/DL-0075-platform-job-registry-and-postgres-advisory-lock.md) registro + advisory lock
  (Alta/Moderada).
- [DL-0076](decision-log/DL-0076-platform-initial-job-catalog-and-scheduler-wiring.md) catálogo + ligação
  (Média/Moderada).
- [DL-0077](decision-log/DL-0077-platform-system-audit-append-only-via-event-listener.md) auditoria append-only
  (Alta/Moderada).
- [DL-0078](decision-log/DL-0078-platform-certificate-signer-graduated-from-billing-stub.md) signer graduado
  (Média/Moderada).

## Riscos / o que fica para a próxima fase

- **DL-0074 (Baixa/Cara):** onde custodiar (KMS×HSM×secret manager) e A1×A3 é decisão de infra do dono; o v1
  cifra at-rest atrás da porta `SecretCipher` — trocar o cofre é trocar o adaptador, mas exige re-cifrar/migrar
  o segredo real. Assinatura ICP-Brasil real (CAdES/XAdES com a chave) também é evolução da porta.
- **Papel TI** para o disparo manual e auditoria de acesso = SPEC-0024/Identity (Fase 8k/13); hoje o ator vem do
  `UserContextProvider` stub.
- **Métricas Prometheus** (`job_runs_total`, `certificate_days_to_expiry`) = Fase 11 (SPEC-0027).
- **Tela Angular** do Platform = Fase 10 (UX).
