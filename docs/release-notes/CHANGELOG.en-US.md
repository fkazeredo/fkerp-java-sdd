# Changelog (en-US)

> 🌐 **Language / Idioma:** **English** · the detailed pt-BR notes live one file per version in this
> same folder ([`0.1.0.md`](0.1.0.md) … [`0.22.1.md`](0.22.1.md)).

Consolidated, English-language history of released versions. The per-version pt-BR files remain the
detailed source; this file is the stakeholder-facing en-US mirror. Versioning follows
[ADR 0015](../adr/0015-semantic-versioning-and-release-management.md) (SemVer `MAJOR.MINOR.PATCH`,
`0.y.z` pre-1.0; each delivered phase bumps the MINOR). Newest first.

---

## 0.23.0 — Phase 13 · Professional Identity/AuthZ (graduates SPEC-0024)

**MINOR — graduates SPEC-0024 to a live external OIDC IdP. Contains a BREAKING change (highlighted per
ADR 0015 §4): the in-house `POST /api/identity/login` is removed — login moves to the provider.**

The ERP becomes an **OAuth2 Resource Server** validating the **external OIDC IdP's (Keycloak) JWTs via
JWKS** (RS256, key rotation), mapping the realm roles (`realm_access.roles`) to Spring authorities — the
role model and the `UserContextProvider` port survive the swap, only the token's *source* changes. This
**resolves the two deferred debts**: **DL-0079** (live external IdP) and **DL-0092** (real
silent-refresh).

- **Added:** Resource Server by **JWKS** (`issuer-uri` + `jwk-set-uri`) validating the external token
  (RS256 signature, `iss`, `exp`) with automatic key rotation (DL-0104).
- **Added:** **`realm_access.roles` → `ROLE_*`** mapping (keeps the SPEC-0024 role catalogue) plus
  `scope` exposed as `SCOPE_*` for future fine-grained checks (DL-0104).
- **Added:** dev **Keycloak** IdP in `docker-compose.yml` and `compose.e2e.yaml` with an imported `acme`
  realm (`infra/keycloak/realm-acme.json`): 6 roles, a **public SPA client (PKCE + refresh)**, an E2E
  direct-grant client (test only), and **seed users** (one per role + `dev`, password `dev12345` —
  dev/E2E only) (DL-0103).
- **Added:** **frontend OIDC** via `angular-oauth2-oidc` — the **"Sign in with SSO"** button
  (code+PKCE) and **real silent-refresh** by refresh token (graduates DL-0092/DL-0106); the
  correlation-id and auth interceptors now skip cross-origin calls (IdP CORS).
- **Added:** **local test JWKS path** (`TestJwtTokens`, a test RSA keypair) — tests mint RS256 tokens in
  the Keycloak shape and exercise the genuine JWKS decoder **without an internet IdP** (DL-0105);
  `TestSecurityConfig` keeps the full-access actor when no `Authorization` header is present.
- **Changed:** `GET /api/identity/me` records the **`AUTH_LOGIN`** first-touch audit (login audit moves
  off the removed `/login`); OpenAPI security scheme → **OIDC bearer (JWKS)**; version 0.22.1 → 0.23.0.
- **Removed (breaking):** `POST /api/identity/login`, the in-house issuer (`JwtIssuer`/HS256), the
  **local user store** (`identity_users`/`user_roles`, BCrypt), the dev seeder/stub and the password
  hasher (DL-0105/DL-0107). The ERP **no longer custodies passwords**.
- **Migration:** **V31** drops `user_roles`/`identity_users` (idempotent); keeps `roles`/
  `role_permissions` (the role→permission catalogue stays local — the enforcement source, DL-0107).
- **Gates:** `./mvnw verify` green (476 backend tests; ArchUnit/Modulith/Spotless/Checkstyle/JaCoCo);
  frontend lint + 56 tests + coverage above thresholds + build; **11 Playwright E2E green** against the
  real Keycloak OIDC flow.

---

## 0.22.1 — Phase 12 · Quality & E2E

**PATCH, test/CI/coverage tooling only — no contract change, no migration, nothing user-facing
(Rule Zero).** Verifiable-quality foundation brought from the sibling fkerp-poc and raised to real
gates:

- **Added:** backend coverage **gate** (JaCoCo) — `./mvnw verify` reports and **fails** below 80%
  instruction coverage; measured 89% with the 477 tests green (DL-0099).
- **Added:** frontend coverage **gate** (`@vitest/coverage-v8`) — `ng test` collects v8 coverage and
  **fails** below the thresholds (statements/lines 65, functions 48, branches 55); measured 70/72/54/60
  with the 57 tests green (DL-0100).
- **Added:** isolated, throwaway **Playwright E2E stack** (`compose.e2e.yaml`): ephemeral tmpfs
  Postgres + backend + Nginx frontend on dedicated ports (4201/8081), so E2E **never touches the dev
  database** (proven: dev stack stays Exited, its volume intact) (DL-0101).
- **Added:** **11 E2E specs** green headless (chromium) — happy path (login → dashboard → navigation →
  account creation) and **sad paths** (invalid credentials, no-session→login redirect, empty state,
  unsaved-changes guard, **401**, **403** by role) (DL-0102).
- **Added:** a **CI E2E job** (`.github/workflows/e2e.yml`) — brings the isolated stack up, waits for
  health, runs Playwright headless and **always** tears it down (`if: always()`); never touches dev
  data. The existing `ci.yml` is unchanged and now also runs the coverage gates.
- **No** new endpoint, DTO, JSON, published event, i18n key, or migration; OpenAPI only bumps to
  **0.22.1**. The user manual is intentionally **unchanged** (nothing user-facing changed).

---

## 0.22.0 — Phase 11 · Observability & monitoring

**MINOR, new retro-compatible capability — no business-rule change, no migration.** The ERP's
observability foundation, brought from the sibling fkerp-poc and adapted to this project's layers
(ADR 0012) and role model (SPEC-0024); it instruments and exposes what already exists (Rule Zero):

- **Added:** `micrometer-registry-prometheus`; deliberate Actuator exposure
  (`health,info,prometheus,metrics`) — `env`/`beans`/`heapdump`/etc. are **not** exposed (DL-0095).
- **Added:** endpoint security — `health`/`info`/`/api/version` **public**; `prometheus`/`metrics`
  require **ROLE_IT** (401 anonymous, 403 without the role, 200 for IT) (DL-0095).
- **Added:** `GET /api/version` → `{ version, gitCommit, buildTime }` from Spring Boot **build-info** +
  the git-commit-id plugin, with **graceful degradation** (absent fields → `"unknown"`) (DL-0097).
- **Added:** **structured JSON logs** (ECS) in the container via `LOGGING_STRUCTURED_FORMAT_CONSOLE`;
  the MDC **correlation id** becomes a JSON field; dev/local keeps the human console. No secret/PII is
  logged (DL-0096).
- **Added:** an `infra/` monitoring stack (mirrors the POC): Prometheus + Loki + Grafana Alloy +
  Grafana via docker-compose, with provisioned datasources and the **"Acme Travel ERP — Backend
  Overview"** dashboard.
- **Added:** **business metrics** (`BusinessMetrics` in `infra.observability`) derived from
  already-published domain events — confirmed/cancelled bookings, composed/overridden quotes, issued
  commission invoices, closed periods, logins (DL-0098); a new ArchUnit rule keeps the domain free of
  Micrometer/Actuator.
- **Changed:** `NoResourceFoundException` now maps to **404** (unknown URL / unexposed actuator
  endpoint) instead of 500; project version 0.21.0 → 0.22.0.
- **No migration, no business-contract change.** `./mvnw verify` green: **477 tests** (was 468);
  ArchUnit (17 rules)/Modulith/Spotless/Checkstyle green. The monitoring stack is config/infra — not
  part of `./mvnw verify`.

---

## 0.21.0 — Phase 10 · UX & professional frontend (frontend-only)

**MINOR, new retro-compatible capability — no API/contract change.** A full UX upgrade of the Angular
frontend without touching any business rule:

- **Added:** PrimeNG 21 (Aura preset via `@primeuix/themes`) + primeicons + `@angular/cdk` +
  `@angular/animations`; Tailwind v4 (`@tailwindcss/postcss`) integrated via CSS layers (DL-0090,
  graduating DL-0003 — the plain-CSS stack from Phase 0).
- **Added:** a SaaS **shell** (sidebar + top bar + responsive drawer) as a layout route; **light/dark
  theme** (`ThemeService`, `.app-dark` selector, `--app-*` tokens, persisted, OS-default — DL-0091).
- **Added:** **command palette** `Ctrl/Cmd+K`, global shortcuts (`g`+key navigation, `?` help) that
  ignore editable fields, and a central command registry (DL-0093).
- **Added:** **login** with **silent session revalidation** on boot and near expiry via
  `GET /api/identity/me` (no refresh token in this phase — DL-0092); the guard/interceptor preserve
  the intended `returnUrl`.
- **Added:** an unsaved-changes (`canDeactivate`) guard; a shared `ScreenState`
  (loading/empty/error/permission) and the repagination of **every** screen with PrimeNG.
- **Added:** a **Dashboard** with KPIs (Accounts/Bookings/Reconciliation/Exchange) computed
  **client-side** from the existing list endpoints (DL-0094); the protected root route is the dashboard.
- **Changed:** feature routes are lazy-loaded (lean initial bundle); project version 0.20.1 → 0.21.0.
- **No migration, no new endpoint.** Frontend gate green (lint + 57 component tests + build); backend
  `./mvnw verify` stays green (468 tests, untouched).

---

## 0.20.1 — Phase 9 · Structural cleanup: drop the `internal` package from the domain (refactor, no user-facing change)

**PATCH, refactor only — no contract change and no new user capability** (Rule Zero). The domain's
implementation types moved out of the `com.fksoft.domain.<module>.internal.*` sub-package into the
module's base package `com.fksoft.domain.<module>.*`. The `internal` folder convention — inherited
from the product's Go version, where `internal/` is a compiler keyword — **no longer exists** under
`com.fksoft.domain`. Nothing in the REST endpoints, DTOs, JSON, published events, i18n keys, database
schema or behavior changed, so the **user manual is intentionally unchanged**.

Flattening would otherwise turn each module's base package into its whole public surface for Spring
Modulith (the unnamed named interface), so Modulith would **stop hiding** the formerly-internal
types. To avoid weakening any gate (CLAUDE.md Rule 5), the boundary was **moved**, not removed:
(1) a type marker `@com.fksoft.domain.ModuleInternal` on every formerly-internal public type
(package-private types stay hidden by the compiler, no marker needed); (2) a new ArchUnit rule
`MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES` — no class of another domain module may depend
on a `@ModuleInternal` type (the module itself and the `infra` layer are exempt — ADR 0010/0012);
(3) a teeth test proving the rule fails when a foreign module touches such a type. The
Intelligence/Portfolio/Platform boundary predicates now recognise the marker instead of the
`.internal` package signal. **20 domain modules** flattened (main + test); ArchUnit now has **16
rules**; Spring Modulith stays acyclic across the 22 modules. **No Flyway migration.** OpenAPI doc
version bumped to 0.20.1 (metadata, not a contract). ADR 0016 + DL-0089. Tag `0.20.1`.

---

## 0.20.0 — Phase 8l · Admin (SPEC-0025) — last sub-phase of the 8x block

`admin` module (the 22nd Modulith module, DL-0084): the **administrative desk** — a **lean registry**
of administrative suppliers (utilities, software/service PJ, self-employed) and their contracts that
feeds expense entries into the **Finance** ledger and references the supporting documents in the
**Compliance** vault. It is a **generic** subdomain delivered as registry + seam — **full procurement**
(quotation/purchase order) is out of scope (buy it if required). **8l-1 (suppliers + contracts + role
gate, BR1/BR2/BR6, DL-0084/0088):** register an `AdminSupplier` (UTILITY | SOFTWARE | SERVICE | OTHER,
born ACTIVE) and `AdminContract` (validity window, recurrence, amount, Compliance document **by
value** — never an FK); an invalid validity window is a 400. **Writes require `ROLE_FINANCE`** (the
expense becomes a financial obligation): without it → **403, audited**; with it → 201. Every change is
audited in the Platform's `system_audit` (`ADMIN_CHANGE`, **metadata only** — a CNPJ/CPF, which may be
a self-employed's personal data, never travels in full). **8l-2 (expense → Finance + required
documents, BR3/BR4, DL-0085/0086):** registering a recurring expense **creates** a **PAYABLE** entry
through the `FinanceService.register` facade (no FK), keeps the `financeEntryId` by value and **lists
the documents** the Compliance requires (the `DocumentRequirementDirectory` read port); the kind maps
the entry type and the documents — UTILITY→`UTILITY_EXPENSE`/[UTILITY_BILL],
autonomous→`AUTONOMOUS_SERVICE`/[RPA], PJ→`SERVICE`/[NFSE], OTHER→`OTHER_EXPENSE`/[]. It is
**idempotent** per `(supplier, period, kind)` (a duplicate is a 409, no second posting). Admin **does
not** impose the document rule nor close a period (BR4) — it only **generates** the entry and
**references** the document; the veto stays Finance+Compliance. **The golden rule holds for the
administrative side too:** a utility expense **without** its bill **blocks the month from closing** (the
regression proves `canClose=false` before attaching, `canClose=true` after). **8l-3 (contract-expiry
alert, BR5, DL-0087):** `AdminContractExpiring` is published by a **controlled-clock governed job**
(30-day horizon, idempotent per contract) under the Platform job governance — an **alert, never a
block**. **Migration V30** (idempotent): `admin_suppliers`/`admin_contracts`/`admin_expenses` (UNIQUE
`(supplier_id, period, kind)`), Finance/Compliance references **by value**; an **additive** Compliance
seed for `SERVICE` (NFSE at registration, payment proof at settlement) **without editing the applied
V8**; the `admin-contract-expiry` job seeded in the Platform catalogue. The Finance `EntryType` gained
`SERVICE`/`OTHER_EXPENSE` (additive). New endpoints under `/api/admin` (suppliers/contracts/expenses/
flag-expiring), OpenAPI **0.20.0**. Decisions **DL-0084..0088** — none is Low-confidence **and**
Expensive-to-reverse (the procurement Open Question was closed by the spec itself; the rest follow
already-validated project patterns). **`./mvnw verify` green: 466 tests** (was 444; +22 Admin: 6 unit
+ 16 integration), ArchUnit 15 rules, Spring Modulith acyclic with the 22nd module, Checkstyle 0
violations. **This closes the 8x block;** phases 9–15 are structural/UX/observability/E2E/IdP/stack
cross-cutting work.

---

## 0.19.0 — Phase 8k · Identity (SPEC-0024)

`identity` module (the 21st Modulith module, DL-0080): it **graduates the dev auth stub** into **real
authentication** with the backend as the **single authorization authority** (security.md). **8k-1
(authentication + JWT login, BR1/BR4, DL-0079/0081):** Spring Security is turned on and the ERP
authenticates **in-house** as the Resource Server of its **own HS256 JWT issuer** — `POST
/api/identity/login` verifies a local user's **BCrypt** password and returns a **bearer JWT** carrying
the user id, username and roles; subsequent calls send `Authorization: Bearer <jwt>` and the real
`JwtUserContextProvider` resolves the user/roles from the verified token, **graduating the stub without
changing the port** the modules consume. Bad credentials return a **generic 401** that never reveals
whether the user exists (BR4). The live external **OIDC IdP** (JWKS/rotation, fine scopes) is **Phase
13** — the `UserContextProvider` port is the seam that survives that swap. **8k-2 (roles/permissions +
access audit, BR2/BR3/BR5, DL-0082/0083):** a `Role`/`Permission` model (closed catalogue) is the single
source of truth of internal authorization; the **sensitive actions** the specs already cite now **require
the matching role** (issue NF → `ROLE_FINANCE`, close period → `ROLE_FINANCE`, trigger job / custody
certificate → `ROLE_IT`, directive → `ROLE_DIRECTOR`, rule → `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN`); a
denial is a **403, audited**. Enforcement is at the HTTP layer (Spring Security `hasRole`) **and**
reaffirms the existing domain check (CommercialPolicy/DL-0038), which now reads roles from the **real
token**. **Login and denials** are recorded in the Platform's append-only `system_audit`
(`AUTH_LOGIN`/`ACCESS_DENIED`) — never a token/secret (BR4); it reuses the 8j seam instead of a new
`access_audit` table (Rule Zero, DL-0083). `GET /api/identity/roles` and `GET /api/identity/access-audit`
are themselves role-protected. **8k-3 (frontend login):** an Angular **login** screen, an `AuthService`
(stores the token, mirrors the token's user), an **interceptor** that attaches the bearer and reacts to a
401, a route **guard** and the current user / sign-out in the shell — the backend stays the authority,
the front only mirrors. **Not breaking the build (the phase's hardest constraint):** the 434 prior tests
assumed a fully-authorized actor; graduating ≠ rip-and-replace, so the security layer stays **mounted,
not removed** — under the `test` profile a `TestSecurityConfig` runs the **same real authorization** but
authenticates a full-access test actor **only when no `Authorization` header is present**, so the old
tests stay green without sending tokens while the new security tests exercise the **genuine 401/403** by
sending tokens. The dev stub stays behind the `dev` profile, off in production (BR6). **V29** creates
`roles`/`role_permissions`, `identity_users` (BCrypt hash only) and `user_roles`, seeds the six base
roles and the named permissions; dev/test seed users are created programmatically (dev/test profiles
only, no hardcoded hash). OpenAPI **0.19.0**; new errors `identity.credentials.invalid` (401),
`auth.unauthenticated` (401), `access.denied` (403), i18n pt-BR + en fallback. **DL-0079** is the phase's
single Low-confidence / Costly-reversibility decision (buy/which IdP is the owner's call; the in-house
issuer delivers the real model now and Phase 13 consolidates the live external OIDC). `./mvnw verify`
green: **444 tests**, ArchUnit **15 rules**, Spring Modulith acyclic with the new `identity` module;
frontend `ng lint` + **18 tests** + `ng build` green.

## 0.18.0 — Phase 8j · Platform (SPEC-0023)

`platform` module: the **operated-infra** context (the 20th Modulith module, DL-0073) that **guards**
secrets, **governs** the automatic routines and **audits** the system — it holds **no business rule**
(BR6, ArchUnit-enforced with teeth). Three slices. **8j-1 (certificate custody, BR1/BR5, DL-0074/0078):**
the e-CNPJ certificate/credentials are custodied with the secret material **encrypted at rest** —
**AES-256-GCM** (authenticated envelope, random IV), the **master key held outside the database** (env),
behind the `SecretCipher` port (`AesGcmSecretCipher` adapter). The private key/password **never** appears
in code, log, event, DTO or the database in clear; only **metadata** is exposed (`GET /api/platform/
certificate/status` → subject, validity, days-to-expiry, status). Signing is the Platform-owned
`CertificateSigner` port; the Billing stub now **delegates** to the custody when a certificate is present
(keeping the `billing.CertificateSigner` port, back-compat, DL-0078). A controlled-clock sweep raises
`CertificateExpiring` (idempotent, 30-day horizon). **8j-2 (job governance, BR2/BR3, DL-0075/0076):** the
`ScheduledJob`/`JobRun` registry and `runWithGovernance(job, window, work)` — **idempotency per
`(job, window)`** (a second run for the same window is SKIPPED), **distributed locking** via a Postgres
**advisory lock** (one instance at a time; a concurrent run gets **409 locked**, the `JobLock` port +
`PostgresAdvisoryJobLock`) and a `JobRun` with start/finish/status/items/correlation id. A failure is
recorded **FAILED and re-raised** — never masked as success (BR3; the `JobRun` closes in a `REQUIRES_NEW`
transaction so it survives the work's rollback). The seeded catalog (V28) is the already-activated jobs
(point-clock crawl, SLA/license/representation/retention/certificate sweeps); the job's **logic stays in
its owner module** (BR6) and the five existing schedulers plus a new `RepresentationExpiryScheduler` now
run through `GovernedJobs`. Endpoints: `GET /api/platform/jobs`, `GET /jobs/runs?job=&status=`,
`POST /jobs/{name}/trigger` (202). **8j-3 (system audit, BR4, DL-0077):** an **append-only**
`SystemAuditEntry` (no mutator) consolidates security/integration/job events with timestamp, actor and
correlation id via an in-process listener of the Platform's exposed events plus a `SystemAuditService.
record(...)` facade for other producers; the detail is **metadata only** — never the secret material
(BR1). `GET /api/platform/audit?actor=&type=&from=&to=` is filterable and paginated. **V28** creates
`platform_certificates` (metadata in clear + encrypted material + key alias), `scheduled_jobs` (seeded)
and `job_runs` (with the `(job_name, idempotency_key)` partial-unique window guard) and `system_audit`
(append-only). OpenAPI **0.18.0**; errors `platform.certificate.{not-found(404),unavailable(503)}` and
`platform.job.{not-found(404),locked(409)}`, i18n pt-BR + en fallback. **DL-0074** is the phase's single
Low-confidence / Costly-reversibility decision (where to custody — cloud KMS × HSM × secret manager — and
A1×A3 is the owner's infra/security call; the `SecretCipher` port lets a real backend be plugged without
touching the domain, but swapping the vault requires re-encrypting/migrating the real secret).
`./mvnw verify` green: **434 tests**, ArchUnit **15 rules** (the new "Platform owns no domain rule" rule
with teeth), Spring Modulith acyclic with the new `platform` module, Spotless/Checkstyle clean.

## 0.17.0 — Phase 8i · People (SPEC-0022)

`people` module (HR side): the **minimal HR** capability built on top of the operational point snapshot
the module has owned since Phase 6 — **collaborators, period journey, time-bank and discrepancies** —
without becoming payroll. It is **built on top** of the clock, **not a crawler rewrite**: the journey is
computed over the **operational snapshot** (treated as **non-legal**, BR6), while the legal artifact (the
signed AFD/AEJ) stays in the Compliance vault. Heavy payroll (eSocial/FGTS/vacation/13th) is **buy/
integrate** (a generic subdomain). Three slices. **8i-1 (V27):** `Employee` has a **unique** identifier,
admission date, contracted daily journey (`HH:mm`, the `ContractedJourney` value object), an
ACTIVE/ON_LEAVE/TERMINATED status (born ACTIVE) and the employment-contract document (Compliance, by
value). **8i-2 (DL-0069/0070/0071):** the pure `JourneyCalculator` computes the **time-bank balance** =
worked − contracted minutes (signed: positive overtime, negative shortfall; a negative bank is allowed —
CLT art. 59) and detects discrepancies (`ODD_PUNCH`/`MISSING_PUNCH`/`INCOHERENT_JOURNAL`). `processJourney`
is **idempotent** per `(employee, period)` and consumes the period's operational snapshot **by value**
(`snapshotRef`, DL-0069); a discrepancy becomes an **alert** in a treatment queue and **never
auto-corrects** (BR4/DL-0071). It publishes `JourneyProcessed` and `JourneyDiscrepancy`. **8i-3
(DL-0072):** an `infra` orchestrator archives the payslip in the Compliance vault as a **PAYROLL**
document (5-year retention, `hasPersonalData=true` — LGPD) referenced by value; People never becomes a
vault. Endpoints under `/api/people`: `POST /employees`, `GET /employees/{id}`, `GET /employees?status=`,
`POST /employees/{id}/journey`, `GET /employees/{id}/journey?period=`,
`GET /employees/{id}/timebank?period=`, `GET /discrepancies?period=&status=`,
`POST /employees/{id}/payslip`. DL-0069…0072 (DL-0070 is the only Low-confidence one — the time-bank
compensation policy is a labor/collective-agreement decision the HR/legal team must confirm; reversal is
Moderate, not Costly). `./mvnw verify` green: 411 tests, ArchUnit 14, Modulith acyclic, 0 Checkstyle.

## 0.16.0 — Phase 8h · Assets (SPEC-0021)

`assets` module (18th): the **internal-patrimony** context — the Acme's own equipment, software licenses
and other goods. A deliberately **lean** registry that ties an asset's cost (Finance) and document
(Compliance) together and alerts on expiring licenses — **not** a full asset-management system (no
depreciation, maintenance or resale stock; buy one if full management is needed — DL-0065). An `Asset`
has a type (EQUIPMENT | SOFTWARE_LICENSE | OTHER), an identifier, an ACTIVE/RETIRED status, the
acquisition date and cost (Money); a SOFTWARE_LICENSE **requires an `expiresAt`** (BR1, else a 400). The
acquisition document (Compliance) and cost ledger entry (Finance) are referenced **by value, never an
FK** (BR2). Retirement is **audited (who/when/reason) and terminal** — retiring twice is a 409,
preserving the first audit (BR4/DL-0068). Assets is **a leaf producer** (DL-0067): it publishes
`AssetRegistered` and `AssetLicenseExpiring` in-process but wires no Finance/Intelligence consumers
(posting a patrimony cost is a business rule the spec does not define). A controlled-clock job flags
active licenses within the 30-day horizon and publishes `AssetLicenseExpiring` **once per license**
(idempotent, an alert that never blocks — DL-0066); `GET /api/assets?expiringWithinDays=N` is the ad-hoc
listing. Assets is **patrimony, not a product** — it never prices a sale (BR5). V26 migration.
DL-0064…0068 (none is Low-confidence + Costly: Q2 was settled by the architect's recommendation —
two contexts). `./mvnw verify` green: 388 tests, ArchUnit 14, Modulith acyclic (18th module), 0
Checkstyle.

## 0.15.0 — Phase 8g · Portfolio (SPEC-0020)

`portfolio` module (17th): the **representation** context — what the Acme represents commercially (it is
a GSA): the **brands/suppliers**, the **representation contracts** that grant the right to sell, and the
**goals per brand** with realized-vs-goal tracking. A brand has a unique `brandRef` (a duplicate is a
translated 409, never a raw constraint) and an ACTIVE/INACTIVE status; a contract holds the validity
window, the Compliance document referenced by value (never an FK) and reference terms (jsonb, not
prices). Selling a brand **without an in-force contract only alerts**, it never blocks (DL-0061); an
expiring contract is signalled **once per contract** by a controlled-clock job that publishes
`RepresentationExpiring` (DL-0063). Goals are VOLUME or REVENUE (BRL), unique per (brand, period,
metric); the **realized is a read-model projection over sales events** — `BookingConfirmed` (VOLUME) and
`SpreadRealized` (REVENUE) matched to a brand by a Portfolio-owned sale-attribution intake, **without
changing the sale event**, idempotent per event (DL-0062, Low-confidence: which field identifies the
brand on a sale is a business decision). Portfolio **never prices, computes commission, nor commands the
sale** — a new ArchUnit rule gives BR6 teeth. V25 migration. DL-0060…0063.

## 0.14.0 — Phase 8f · Marketing (SPEC-0019)

`marketing` module (16th): B2B marketing with **LGPD consent as a first-class citizen**. Consent is an
append-only log (current state = the latest row per subject+purpose; revoke/re-consent append rows,
single opt-in in v1). A campaign sends **only** to subjects with a GRANTED consent — the rest are
suppressed and counted — through a `NewsletterSender` ACL (traceable mock; the provider DTO never
crosses into the domain), idempotently per recipient. Segments use validated criteria over existing
data (closed catalog, minimization). Attribution links a campaign code to a booking and, on
`BookingConfirmed`, publishes `CampaignConverted` for the DSS (`BookingConfirmed` is unchanged). LGPD
erasure removes marketing PII while preserving an anonymized revocation tombstone (so the subject is
never silently re-included), keeping attributions and other legal bases intact. Not a CRM — the
consent/attribution layer (full CRM = buy). V24 migration. DL-0055…0059 (DL-0058 is Low-confidence /
Costly-to-reverse: the LGPD erasure scope is a DPO decision and the purge is destructive).

## 0.13.0 — Phase 8e · AfterSales (SPEC-0018)

`aftersales` module (15th): the post-sale context — support cases with a lifecycle state machine and
**governed SLA deadlines** (resolved from CommercialPolicy: 24h first response / 72h resolution / 48h
cancellation-refund; an SLA breach is a non-blocking alert). Resolving a case orchestrates the owners:
a refund is forwarded to Payout (idempotently, never cancelling the supplier obligation — the merchant
trap holds) and a cancellation to Booking; it accrues the cost-to-serve the DSS uses. DL-0052…0054.

## 0.12.0 — Phase 8d · Payout (SPEC-0017)

Supplier payout / settlement / refund with cents-exact installments. Payment ACL via an idempotent
webhook (ADR 0006); `SupplierSettled` posts to Finance exactly once; payment receipt produced. The
"merchant trap" is preserved (charges never net themselves out). DL-0048…0051.

## 0.11.0 — Phase 8c · Billing (SPEC-0016)

`billing` module (13th): commission NFS-e (base = the commission, not the gross) + ISS by tax regime
(swappable strategy) + NFS-e ACL (mock); posted to Finance via event; the file in the vault satisfies
the requirement. DL-0044…0047.

## 0.10.0 — Phase 8b · Finance (full) (SPEC-0015)

Automatic AP/AR entries per event (idempotent) + per-currency trial balance. Buy-vs-build reaffirmed
(cash book, not a full GL). The close-veto regression stays green. DL-0041…0043.

## 0.9.0 — Phase 8a · CommercialPolicy (SPEC-0014)

Precedence engine (Directive > Promotion > Contract > Policy > Default) + the markup stub graduated
without breaking Quoting (the `MarkupProvider` contract intact; `source` = the winning level).
DL-0037…0040.

## 0.8.0 — Phase 7 · Intelligence / DSS (SPEC-0013)

`intelligence` module (12th) + `PromoFxAdvisor` + `OverrideNudge` behind a flag; "advises, never
commands" enforced by an ArchUnit rule + a teeth test; LLM `InsightNarrator` port (stub). DL-0034…0036.

## 0.7.0 — Phase 6 · Point-clock crawler (SPEC-0012)

`people` module (11th) + crawler with circuit-breaker / dead-letter + ingestion of the signed AFD/AEJ
into the vault (5-year retention). DL-0029…0033.

## 0.6.0 — Phase 5 · Exchange exposure + reports (SPEC-0011)

Market rate + subsidy × drift (`FxPosition`) + reports (`LiveExposure` / `PromoFxResult`, 2% drift
alert). DL-0025…0028. A Modulith cycle was caught by the gate and fixed (reconciliation → exchange).

## 0.5.0 — Phase 4 · Cancellation + merchant trap (SPEC-0010)

`CancellationPolicy` + the merchant trap (charges never net out) + no-show. DL-0020…0024.

## 0.4.0 — Phase 3 · First real integration / ACL (SPEC-0009)

Sourcing + the `INTEGRATED` branch (trusts the external price, no recompose) + an inbound webhook ACL
(HMAC, idempotent, external DTO confined to `infra.integration`). DL-0016…0019.

## 0.3.0 — Phase 2 · Minimal compliance (SPEC-0008 + Finance seam SPEC-0015)

Finance AP/AR seam + period close + Compliance vault + mandatory attachment + the monthly-close veto +
retention. DL-0012…0015.

## 0.2.1 — Phase 1 · Commercial core (front-end)

5 Angular screens (Accounts / Exchange / Quoting / Booking / Reconciliation) + navigation; lint / test
/ build green. Phase 1 closed end-to-end.

## 0.2.0 — Phase 1 · Manual commercial core (SPEC-0002…0007)

7 Modulith modules, 6 slices: Accounts, Exchange, Commissioning, Quoting, Booking, Reconciliation, on
the shared `Money` kernel.

## 0.1.0 — Phase 0 · Foundation (SPEC-0001)

Walking skeleton: boots, connects to Postgres, `/api/system/health`, minimal Angular screen, ArchUnit +
Spring Modulith + CI green. Event Storming captured.
