# Plano — Fase 8j (Platform: custódia e-CNPJ + governança de jobs + auditoria de sistema)

> Spec: **SPEC-0023** (Related ADRs 0011, 0012, 0014; herda SPEC-0001). Base: `origin/develop`
> @46b6db4 (release 0.17.0; fases 0–8i; 19 módulos Modulith). Release-alvo: **0.18.0**. Migração: **V28**.

## Objetivo da fase

Entregar o **contexto Platform** (TI / infra operada), conforme OVERVIEW Parte 6/SPEC-0023:

1. **Custódia segura do e-CNPJ (ICP-Brasil) e credenciais** — porta `CertificateCustody`/`CredentialVault`
   + adaptador; certificado/senha **nunca** em código, log, evento, DTO ou banco em claro (BR1). Só
   metadados (titular/validade/fingerprint/status). Assinatura via porta `CertificateSigner` (gradua o
   stub do Billing/DL-0046). Alerta de expiração `CertificateExpiring` (BR5).
2. **Governança de jobs** — registro `ScheduledJob` + `JobRun` (histórico, idempotência por janela,
   locking distribuído, correlation id), e o **scheduler** que orquestra os jobs já existentes (crawler
   de ponto SPEC-0012, varreduras 8e/8g/8h). Falha registra `FAILED`, nunca vira sucesso (BR2/BR3).
3. **Auditoria de sistema** — `system_audit` append-only consolidando eventos de
   segurança/integração/jobs com timestamp/ator/correlation id (BR4).

Platform **não** contém regra de domínio de negócio (BR6) — ArchUnit garante.

## Decisões (decision-log) tomadas antes do código

| DL | Tema | Confiança | Reversib. |
|---|---|---|---|
| DL-0073 | Novo módulo `domain.platform` (20º Modulith); contexto Platform real (gradua o seam adiado da DL-0030) | Alta | Moderada |
| DL-0074 | **Custódia do e-CNPJ: criptografia at-rest AES-256-GCM (envelope), chave fora do banco; metadados em claro, material cifrado; nunca em log/evento/DTO** | **Baixa** | **Cara** |
| DL-0075 | Governança de jobs: registro `ScheduledJob`/`JobRun` + idempotência por `(job_name, window)` + **lock no Postgres** (advisory lock); sem ShedLock/Quartz (Regra Zero) | Alta | Moderada |
| DL-0076 | Catálogo inicial de jobs e *seed* dos `scheduled_jobs` (crawler de ponto, varreduras de SLA/licença/representação/retenção); schedulers existentes passam a registrar `JobRun` via porta | Média | Moderada |
| DL-0077 | Auditoria de sistema append-only consolidada por **listener de eventos in-process** + escrita direta da porta de jobs/custódia; sem material sigiloso | Alta | Moderada |
| DL-0078 | `CertificateSigner` (porta) **graduado** para o módulo `platform`; o stub do Billing passa a delegar ao signer da custódia (back-compat do contrato; sem expor material) | Média | Moderada |

> **Destaque (Baixa/Cara):** DL-0074 — o **mecanismo de custódia/criptografia e onde a chave mestra
> mora** (KMS de nuvem × HSM × secret manager on-prem; A1 arquivo × A3 token) é **decisão de
> infra/segurança do dono** (Open Question da spec). Adotamos a opção mais defensável (envelope
> AES-256-GCM com chave mestra injetada por ambiente, material cifrado no banco, metadados em claro),
> atrás de uma **porta** trocável — quando o dono escolher KMS/HSM real, troca-se só o adaptador.

## Fatias (ordem de dependência)

### 8j-1 — Custódia do e-CNPJ (segredo) · BR1, BR5
- Migração `V28__create_platform.sql` (parte 1): `platform_certificates(id, subject, holder_document,
  fingerprint, valid_from, valid_until, status, encrypted_material bytea, key_alias, created_at,
  created_by, version)` — **material cifrado**, metadados em claro.
- Domínio `platform`: `PlatformCertificate` (entidade; **nunca** expõe material), `CertificateStatus`
  (VALID/EXPIRING/EXPIRED/REVOKED), `CertificateView` (só metadados), `CertificateCustody` (porta de
  guarda/leitura), `CertificateSigner` (porta de assinatura — graduada), `CertificateExpiring` (evento).
- Porta `SecretCipher` (cifra/decifra) + adaptador `AesGcmSecretCipher` em `infra.platform` (chave por
  ambiente). Exceções: `CertificateNotFoundException` (404), `CertificateUnavailableException` (503).
- Endpoint `GET /api/platform/certificate/status` — **só metadados**. Job de expiração
  `flagExpiringCertificates(now)` (relógio controlado, idempotente) publicando `CertificateExpiring`.
- Billing `StubECnpjCertificateSigner` passa a delegar ao `CertificateSigner` do Platform (DL-0078) —
  contrato intacto; sem material em log.
- **Testes:** unit (status por validade; cifra round-trip; assinatura sem expor material com adaptador
  fake); integração (status só metadados; material nunca em claro no banco/log/erro — regressão de
  segurança); ArchUnit (Platform sem regra de domínio; DTO de custódia não vaza material).

### 8j-2 — Governança de jobs · BR2, BR3
- Migração `V28` (parte 2): `scheduled_jobs(name PK, cron, enabled, owner_module, last_run_at)` +
  `job_runs(id PK, job_name, started_at, finished_at, status, items, failure_class, correlation_id,
  idempotency_key, INDEX ix_job_runs_job_status, UNIQUE parcial p/ idempotência por janela)`.
- Domínio `platform`: `ScheduledJob`, `JobRun` (entidades), `JobStatus` (RUNNING/SUCCEEDED/FAILED/SKIPPED),
  `JobRunView`/`ScheduledJobView`, `JobRunStarted`/`JobRunFinished` (eventos), `PlatformJobService`
  (registrar início idempotente, fechar com sucesso/falha, locking). Porta `JobLock` (lock distribuído)
  + adaptador `PostgresAdvisoryJobLock` em `infra.platform`.
- `JobRegistry`/`JobExecutionTemplate`: um job roda via `runWithGovernance(jobName, window, work)` —
  idempotência por `(job_name, window)`, lock por nome, `JobRun` com início/fim/status/itens/correlation.
- Endpoints: `GET /api/platform/jobs`, `GET /api/platform/jobs/runs?job=&status=&page=&size=`,
  `POST /api/platform/jobs/{name}/trigger` (202). Exceções: `JobNotFoundException` (404),
  `JobLockedException` (409). Seed dos jobs do catálogo (DL-0076).
- Schedulers existentes (`PointClockCrawlScheduler`, `AfterSalesSlaScheduler`, `AssetLicenseExpiryScheduler`,
  `RetentionExpiryScheduler`, e o de representação) passam a registrar `JobRun` via a porta (sem mudar a
  lógica de cada job, que continua no módulo dono).
- **Testes:** unit (idempotência da janela; transições de `JobRun`; falha não vira sucesso); integração
  (dois disparos concorrentes → um roda, outro `locked`; job falho grava `FAILED`; histórico paginado).

### 8j-3 — Auditoria de sistema · BR4
- Migração `V28` (parte 3): `system_audit(id PK, occurred_at, actor, type, detail_json jsonb,
  correlation_id)` — append-only.
- Domínio `platform`: `SystemAuditEntry` (entidade append-only; sem material sigiloso), `SystemAuditView`,
  `SystemAuditService` (registrar + consultar com filtro). Listener in-process consolidando
  `JobRunStarted`/`JobRunFinished`/`CertificateExpiring` (e um ponto de entrada para eventos de
  segurança/integração) → linha de auditoria (DL-0077). Mascara dado pessoal/segredo.
- Endpoint `GET /api/platform/audit?actor=&type=&from=&to=&page=&size=`.
- **Testes:** unit (mascaramento; append-only — sem update/delete); integração (job e certificado geram
  linha de auditoria com ator/correlation; segredo nunca aparece — regressão de segurança).

### 8j-4 — Docs bilíngues + release
- `docs/MANUAL.md` (pt-BR) + `docs/MANUAL.en-US.md` (en-US) em sincronia: nova seção Platform
  (operador/TI vê status do certificado, catálogo/histórico de jobs, disparo manual, auditoria).
- `docs/release-notes/0.18.0.md` (pt-BR) + append em `docs/release-notes/CHANGELOG.en-US.md`.
- OpenAPI: `OpenApiConfig` descrição + versão `0.18.0`.
- Caderno de testes por fatia em `docs/test-report/` + INDEX. Relatório final em `docs/`.

## Portões (ligados desde o 1º commit)
- `cd backend && ./mvnw verify` verde (Docker/Testcontainers): JUnit + ArchUnit + Spring Modulith
  (`ModularityTests.verify()` com o novo módulo) + Checkstyle/Spotless.
- ArchUnit novo: **Platform não importa domínio de negócio** (BR6) — teeth test planta violação e falha;
  **material do certificado não cruza para DTO/evento** (custódia exposta só por metadados).
- `HttpErrorMappingCompletenessTest`: toda exceção nova mapeada.
- Migração V28 idempotente; sem editar migração aplicada; sem FK cross-contexto; eventos in-process.

## Riscos / o que fica para a próxima fase
- **DL-0074 (Baixa/Cara):** custódia real (KMS/HSM/secret manager) e A1×A3 dependem do dono — a porta
  permite trocar o adaptador sem refator de domínio.
- Autenticação/papéis (papel TI para o `trigger`) é **SPEC-0024/Identity** (Fase 8k/13) — aqui o ator vem
  do `UserContextProvider` stub; o gate por papel entra com a auth real.
- Métricas Prometheus (`job_runs_total`, `certificate_days_to_expiry`) ficam expostas como contadores de
  domínio/observabilidade; o stack Prometheus/Grafana completo é a Fase 11 (SPEC-0027).
