# DL-0073 — Novo módulo `domain.platform` (contexto Platform real, 20º Modulith)

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (Scope/BR6), OVERVIEW Parte 5/6 (Platform como Supporting context)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

Até a Fase 6, a DL-0030 decidiu **não criar um módulo `platform` vazio**: o crawler técnico ficou em
`infra.integration.pointclock` e os schedulers em `infra.jobs`, "sem módulo `platform` vazio". A
SPEC-0023 agora entrega o **contexto Platform como capacidade real** (custódia, governança de jobs,
auditoria). Faltava decidir **se** o Platform vira um módulo Modulith de domínio ou continua só em
`infra`.

## Decisão

Criar o módulo de domínio **`com.fksoft.domain.platform`** (`@ApplicationModule(displayName =
"Platform")`), o **20º** módulo de negócio Modulith. Ele é dono de:

- **Custódia** (metadados do certificado + porta de guarda/assinatura) — a entidade `PlatformCertificate`
  (sem material em claro), as portas `CertificateCustody`/`CertificateSigner`, o evento
  `CertificateExpiring`.
- **Governança de jobs** — `ScheduledJob`/`JobRun` (registro, idempotência, histórico), a porta
  `JobLock`, os eventos `JobRunStarted`/`JobRunFinished`, a fachada `PlatformJobService`.
- **Auditoria de sistema** — `SystemAuditEntry` (append-only) + `SystemAuditService`.

Os **adaptadores técnicos** (cifra AES-GCM, advisory lock no Postgres, assinatura) vivem em
`com.fksoft.infra.platform` (porta no domínio, impl na infra — ADR 0010). A **lógica de cada job**
continua no módulo dono (BR6/SPEC-0023 Scope); Platform só **orquestra/guarda/audita**.

## Justificativa

- **SPEC-0023 entrega o contexto** com linguagem, ciclo e dono próprios (custódia, jobs, auditoria) —
  exatamente o critério de `modules-and-apis.md` para um módulo. Já **não** é um "módulo vazio" (a
  ressalva da DL-0030): há agregados, regras de idempotência/locking e fachadas reais.
- **Regra Zero respeitada:** o módulo nasce com capacidade concreta, não especulativa. O que é técnico
  (cifra, lock) fica em `infra` atrás de porta — sem `*Impl`, sem camadas de cerimônia.
- **Fronteira Modulith:** Platform é praticamente **folha** no grafo de comando — os schedulers
  (`infra`) o dirigem; ele consome eventos para auditar. Não chama fachadas de comando de outros
  módulos de negócio, então não cria ciclo.

## Alternativas descartadas

- **Continuar só em `infra` (sem módulo de domínio):** custódia/jobs/auditoria têm regra de negócio
  própria (idempotência, append-only, status de certificado) que não pode morar em `infra` (que é
  driven adapter). Esconderia o contexto que a OVERVIEW lista explicitamente.
- **Dobrar dentro de outro módulo (ex.: compliance):** acoplaria custódia/jobs a um contexto com outra
  linguagem; Compliance é cofre de **documentos**, não de **segredos/jobs**.

## Impacto

- **Specs:** SPEC-0023 — Scope vira "ASSUMIDO (ver DL-0073)".
- **Arquivos:** novo pacote `domain.platform` (+ `internal`), `infra.platform`, controller
  `PlatformController`, package-info com `@ApplicationModule`.
- **Migração:** V28 cria as tabelas do Platform.
- **Gates:** `ModularityTests.verify()` passa a contar 20 módulos; novo ArchUnit "Platform sem regra de
  domínio" (BR6).

## Como reverter

Como o técnico está atrás de portas, recolapsar o Platform em `infra` exigiria mover as fachadas/regras
de volta — refator **Moderado** e localizado, sem tocar contratos REST (os endpoints `/api/platform/*`
permaneceriam). Não há dado destruído.
