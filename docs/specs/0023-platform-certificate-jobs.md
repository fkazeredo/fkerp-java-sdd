# 0023 - Platform (Certificado e-CNPJ, Jobs/Scheduler e Auditoria de Sistema)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001** (que montou o esqueleto: health, i18n, observabilidade,
> `UserContextProvider` stub). Esta spec entrega as capacidades de **Platform como contexto**:
> custódia de **certificado/credenciais**, **governança de jobs** (orquestra o crawler) e **auditoria de
> sistema** (redesenho linha 128/158, 263).

## Goal

Prover a **infra operada** que os módulos fiscais e de integração dependem: **custódia segura do
e-CNPJ (ICP-Brasil)** e das **credenciais** (usado por Billing para NFS-e e pelo crawler para AFD/portal),
o **agendador e o registro de jobs** com idempotência/locking/histórico (orquestra o crawler da
SPEC-0012), e a **auditoria de sistema** centralizada.

## Scope

**Em escopo:** a porta `CertificateSigner`/`CredentialVault` e seu adaptador (custódia segura — KMS/HSM/
secret manager; certificado **nunca** em código/log/banco em claro); o registro `ScheduledJob` +
`JobRun` (histórico, idempotência, locking distribuído) e o **scheduler** que dispara jobs (crawler de
ponto, feed de câmbio, expiração de PENDING, retenção, SLA); o **system audit log** consolidado (eventos
de segurança/integração/jobs).

**Fora de escopo:** a **lógica** de cada job (mora no módulo dono — ex.: crawler na SPEC-0012); a
**autenticação de usuário/papéis** (Identity, SPEC-0024); a emissão de NF em si (Billing, SPEC-0016).

## Business Context

O e-CNPJ é **peça obrigatória** de NFS-e/NF-e, SPED/eSocial e assinaturas do ponto (REP-A/REP-P) — e não
pode vazar (263). Vários módulos têm **jobs** importantes; sem governança única (idempotência, locking,
histórico), eles viram pontos cegos. Platform centraliza essas duas dores (segredo e orquestração) e a
auditoria de sistema.

## Business Rules

```txt
BR1  O certificado/credenciais MUST ser custodiado em armazenamento seguro (KMS/HSM/secret manager),
     acessível só via porta; MUST NUNCA aparecer em código, log, mensagem de erro ou banco em claro
     (`security.md`). Assinar é operação da porta (CertificateSigner), sem expor o material.
BR2  Cada execução de job MUST passar pelo registro: idempotência (não roda duplicado para a mesma
     janela), locking (uma instância por vez) e JobRun com início/fim/status/itens/correlation id.
BR3  Um job que falha MUST registrar a falha e respeitar política de retry/circuit breaker do seu
     adaptador; o scheduler NÃO mascara falha como sucesso.
BR4  O system audit log MUST consolidar eventos de segurança/integração/jobs com timestamp, ator,
     correlation id — append-only.
BR5  Expiração do certificado MUST gerar alerta (CertificateExpiring) — risco fiscal/operacional.
BR6  Platform MUST NOT conter regra de negócio de domínio; é infra operada (orquestra, guarda, audita).

BR7  ASSUMIDO (ver DL-0073): Platform é um **módulo de domínio** próprio (`com.fksoft.domain.platform`,
     20º Modulith) — custódia, governança de jobs e auditoria; os adaptadores técnicos (cifra, lock,
     assinatura) vivem em `com.fksoft.infra.platform` atrás de portas (ADR 0010).
BR8  ASSUMIDO (ver DL-0074): a custódia cifra o material **at-rest com AES-256-GCM** (envelope/AEAD),
     com a **chave mestra fora do banco** (ambiente), via porta `SecretCipher`; só metadados são
     expostos; o material nunca em log/evento/DTO/banco em claro. KMS/HSM real e A1×A3 = troca de
     adaptador (decisão de infra/segurança do dono — segue em aberto).
BR9  ASSUMIDO (ver DL-0075/DL-0076): governança = registro `ScheduledJob`/`JobRun` + idempotência por
     `(job, window)` + **lock no Postgres** (advisory lock, sem ShedLock/Quartz); catálogo inicial =
     os jobs já ativados (crawler, SLA, licença, representação, retenção, certificado), seedados na V28;
     os schedulers existentes registram `JobRun` via porta, sem mover a lógica do dono.
BR10 ASSUMIDO (ver DL-0077): a auditoria de sistema é **append-only**, consolidada por listener
     in-process dos eventos expostos do Platform + a fachada `SystemAuditService.record(...)` para
     produtores de segurança/integração; detalhe só metadados, nunca segredo.
BR11 ASSUMIDO (ver DL-0078): a porta `CertificateSigner` é do Platform; o stub do Billing **delega** à
     custódia (mantém a porta `billing.CertificateSigner`, back-compat) sem expor o material.
```

## Input/Output Examples

```http
GET /api/platform/jobs/runs?job=point-clock-crawl&status=FAILED
200 OK  { "items":[ {"runId":"r9...","startedAt":"...","status":"FAILED","failureClass":"UNAVAILABLE"} ] }

GET /api/platform/certificate/status
200 OK  { "subject":"ACME TRAVEL LTDA:NN...", "validUntil":"2026-11-30", "daysToExpiry": 157,
          "status":"VALID" }     # material do certificado NUNCA é retornado
```

## API Contracts

- `GET /api/platform/jobs` / `GET .../jobs/runs?job=&status=&page=&size=` → catálogo + histórico de execuções.
- `POST /api/platform/jobs/{name}/trigger` — disparo manual (papel TI). → 202.
- `GET /api/platform/certificate/status` — **só metadados** (validade/subject), nunca o material.
- `GET /api/platform/audit?actor=&type=&from=&to=&page=&size=` → auditoria de sistema.
- OpenAPI atualizada. (Custódia/assinatura são **portas**, não endpoints que expõem segredo.)

## Events

- `JobRunStarted` / `JobRunFinished` — `{runId, job, status, occurredAt}`. Produtor: `platform`.
  Consumidor: observabilidade/alerta, `intelligence` (higiene operacional).
- `CertificateExpiring` — `{validUntil, daysToExpiry, occurredAt}` (alerta). Consumidor: governança/TI.

## Persistence Changes

```txt
V23__create_platform.sql
  scheduled_jobs( name varchar PK, cron varchar not null, enabled boolean not null default true,
                  owner_module varchar not null, last_run_at timestamptz null )
  job_runs( id uuid PK, job_name varchar not null, started_at timestamptz not null,
            finished_at timestamptz null, status varchar not null, items int null,
            failure_class varchar null, correlation_id varchar null,
            INDEX ix_job_runs_job_status (job_name, status) )
  system_audit( id uuid PK, occurred_at timestamptz not null, actor varchar null, type varchar not null,
                detail_json jsonb null, correlation_id varchar null )       -- append-only
-- certificado/credenciais NÃO ficam aqui: vão para o secret manager/KMS via porta (BR1)
```

Locking distribuído para jobs (ex.: ShedLock/lock no Postgres). A assinatura usa o `CertificateSigner`
(porta) — Billing/crawler dependem dela sem ver o material.

## Validation Rules

- Segurança: material sigiloso só via porta; nunca persistido/logado em claro (BR1).
- Application: idempotência + locking de job (BR2); auditoria append-only (BR4).
- Princípio: sem regra de domínio (BR6); ArchUnit garante que Platform não importa domínio de negócio.

## Error Behavior

`platform.job.not-found` → 404; `platform.certificate.unavailable` → 503 (custódia indisponível);
`platform.job.locked` → 409 (já em execução). i18n em `messages_pt_BR.properties`. Erros **nunca**
expõem segredo/credencial.

## Observability Requirements

- Logar início/fim/falha de job e acessos à custódia como evento de **sistema** (runId, job, ator,
  correlation id), **sem material sigiloso**. Métricas: `job_runs_total{job,status}`,
  `job_failures_total{class}`, `certificate_days_to_expiry`.

## Tests Required

- **Unit:** idempotência/locking do registro de job; assinatura via porta sem expor material (adaptador fake).
- **Integração (Testcontainers):** dois disparos concorrentes do mesmo job → um roda, outro vê `locked`;
  job falho registra `JobRun FAILED` (não vira sucesso); status do certificado retorna só metadados.
- **Regressão (segurança):** nenhum log/erro contém o material do certificado/credencial (falha antes,
  passa depois).

## Acceptance Criteria

- O crawler/feed/expirações rodam pelo scheduler com histórico e sem duplicar execução.
- Billing assina a NFS-e via porta sem o material vazar; a validade do certificado é monitorada e alerta.
- A auditoria de sistema consolida eventos de segurança/integração/jobs.
- `./mvnw verify` verde (ArchUnit confirma Platform sem regra de domínio).

## Open Questions

- **Onde** custodiar (KMS de nuvem × HSM × secret manager on-prem) e A1 (arquivo) × A3 (token) — decisão
  de infra/segurança do dono. **RESOLVIDA NO v1 (ASSUMIDO — ver DL-0074, BR8):** envelope AES-256-GCM
  com chave mestra por ambiente, atrás de porta `SecretCipher`; KMS/HSM real e A1×A3 trocam só o
  adaptador. **Confiança=Baixa / Reversibilidade=Cara** — segue dependendo da decisão de infra do dono.
- Ferramenta de **locking/scheduler** (ShedLock, Quartz, lock no Postgres) — escolha de implementação.
  **RESOLVIDA (ASSUMIDO — ver DL-0075, BR9):** `@Scheduled` + registro `JobRun` + **advisory lock no
  Postgres**, sem ShedLock/Quartz (Regra Zero).
- Quais jobs entram no catálogo inicial (crawler, feed de câmbio, expiração de PENDING, retenção, SLA) —
  confirmar conforme as fatias forem ativadas. **RESOLVIDA (ASSUMIDO — ver DL-0076, BR9):** os jobs já
  ativados (crawler, SLA, licença, representação, retenção, certificado); feed de câmbio e expiração de
  PENDING entram quando houver job dedicado ativado.

## Out of Scope

Lógica de cada job (módulo dono), autenticação/papéis de usuário (SPEC-0024), emissão de NF (SPEC-0016).
