# Changelog (en-US)

> 🌐 **Language / Idioma:** **English** · the detailed pt-BR notes live one file per version in this
> same folder ([`0.1.0.md`](0.1.0.md) … [`0.36.0.md`](0.36.0.md)).

Consolidated, English-language history of released versions. The per-version pt-BR files remain the
detailed source; this file is the stakeholder-facing en-US mirror. Versioning follows
[ADR 0015](../adr/0015-semantic-versioning-and-release-management.md) (SemVer `MAJOR.MINOR.PATCH`,
`0.y.z` pre-1.0; each delivered phase bumps the MINOR). Newest first.

---

## 0.36.0 — Phase 19d · Real API documentation (springdoc)

**MINOR — documentation quality. No endpoint/JSON shape changed; only the doc content and a
committed contract snapshot.**

springdoc was already in the stack but empty. This slice makes the API docs real and usable.

- **`@Tag` on all 37 controllers** (business name + description) → the Swagger UI groups the API by
  context. **Fitness function** (ArchUnit): every `@RestController` must carry an `@Tag`.
- **Error contract documented globally** (`GlobalErrorResponsesCustomizer`): the stable
  `{ code, message, fields }` envelope (ADR 0011) + 400/401/403/404/409/422 on every operation,
  without repeating `@ApiResponse` on ~75 endpoints.
- **Working Authorize button**: OAuth2 **Authorization Code + PKCE** against the self-hosted AS
  (client `acme-erp-web`, ADR-0018) — an operator logs in from the Swagger UI and tries a role-gated
  endpoint; a `bearerAuth` scheme also allows pasting a token.
- **Slim `Info.description`**: the changelog moved back to `docs/release-notes/` (single source).
- **Committed contract snapshot** (`docs/api/openapi.json`) + **drift test**
  (`OpenApiSnapshotIntegrationTest`): changing the contract without updating the snapshot fails the
  build (regenerate with `-Dopenapi.snapshot.write=true`). Generalizes the Phase-18 contract
  invariant to the whole API.
- Gates green: backend `./mvnw verify` (ArchUnit **18** with the `@Tag` rule + the snapshot drift
  test). Follow-up: per-endpoint `@Operation` summaries are incremental.

---

## 0.35.0 — Phase 19c · Hardening (secrets fail-fast, webhook anti-replay, vault upload, login lockout)

**MINOR — security hardening. No user-facing `/api` shape changed; the 2 M2M webhooks now require a
timestamp header (breaking for the machine peers, which are ours/emulated).**

Third slice of Phase 19: closes the behind-the-scenes security risks the audit flagged. Nothing
end-user-visible changes (the MANUAL is untouched — Rule Zero).

- **Webhook anti-replay (DL-0122):** the HMAC now signs **`timestamp + "." + body`** with a
  tolerance window (default 300s), via a single shared `WebhookSignatures` helper (de-duplicating
  the two verifiers). A validly-signed body can no longer be replayed forever. New timestamp header
  required on both webhooks.
- **Production fail-fast (DL-0123):** `ProdReadinessValidator` (prod profile) **refuses to boot**
  with a dev/blank secret (webhook secrets, `PLATFORM_SECRET_KEY`, the `acme` DB password), an
  `http://` issuer, or `billing.tax.regime-confirmed=false` (the real-NFS-e gate — DL-0121). No
  secret value is logged.
- **Vault upload hardening (DL-0124):** binary uploads (pdf/png/jpg) are validated by **magic
  bytes** — closing the doc↔code gap where "never trust the extension alone" was only Javadoc;
  `fileRef` is validated as a UUID on read/delete (path-traversal defense in depth).
- **Login lockout (DL-0125):** brute-force protection on the embedded AS form login — **5
  consecutive failures → 15-minute lock** (DB counter, V38) via `accountLocked` in the
  `UserDetailsService`; generic error (BR4). Plus a minimal password policy (≥8, not a single
  repeated char).
- Gates green: backend `./mvnw verify` **552 tests** (+24), ArchUnit/Modulith/Spotless/Checkstyle/
  JaCoCo unchanged. Migration V38.

---

## 0.34.0 — Phase 19b · Integration quarantine + directed decision-log review

**MINOR — new capability. The webhook wire contract is unchanged; new endpoints are additive.**

Second slice of Phase 19: the owner-requested **serious decision-log review**, researched per
decision and applied in code.

- **Integration quarantine (DL-0120, revises DL-0017):** an inbound quotation rejected for an
  unknown account is **no longer lost** — the external 422 stands, but the translated payload is
  kept in `inbound_quarantine` (own transaction, idempotent per external id, partial unique
  index). The *Offer sourcing* screen gains a quarantine section: list, **replay** (creates the
  INTEGRATED quote once the cause is fixed; a persisting cause keeps it pending) and **discard**.
  Signature failures quarantine **nothing**. Migration **V37**; 5 new backend E2E tests.
- **Tax refinement (DL-0121, refines DL-0044):** research found the risk-raising nuance — travel
  agency (CNAE 7911-2/00) → Anexo III without Fator R, but **commercial representation (GSA
  revenue) → Anexo V with Fator R**. Recorded in SPEC-0016 (Q7) for the accountant + new
  **`billing.tax.regime-confirmed`** flag (default `false`) that the 19c production-readiness
  validator will require before real NFS-e issuance.
- **Kept with reinforced justification** (review addendum in each DL file): DL-0009 (price
  formula), DL-0029 (REP-P), DL-0049 (settlementRate/FX legal framework), DL-0058 (LGPD
  tombstone), DL-0070 (CLT art. 59), DL-0074 (AES-GCM + **A1**/KMS recommendation).
- **Enum→cadastro made a standing rule** (owner request): CLAUDE.md invariant 7 +
  `architecture/backend.md` "Enums vs cadastro" section (ADR-0019 codified).

---

## 0.33.0 — Phase 19a · Default-deny role-based authorization (full matrix)

**MINOR — security hardening. No data shape changed; the authorization STATUS changes for callers
without the owning desk's role (2xx → 403), highlighted below.**

First slice of **Phase 19 (maturity refactoring)**: the role enforcement that covered only ~8 routes
(DL-0082) now covers the **entire API surface** with **default-deny** for anything unmapped.
SPEC-0024 BR18; DL-0119.

- **Single ordered matrix** (`ApiAuthorizationMatrix`): every write endpoint carries its owning
  desk — **Finance** (ledger/invoices/payouts/back-office/reconciliation settlement/vault purge),
  **Operations** (accounts/offers/quotes/bookings/after-sales/market rate/cancellation
  policy/marketing/portfolio), **IT** (people/time-clock/assets/platform), **Director** (pinned
  rate, directives, LGPD erasure), **Policy Curator** (reference data). **Viewer writes nothing.**
- **Default-deny:** `POST/PUT/PATCH/DELETE /api/**` outside the matrix is refused; completeness is
  a **build gate** (`ApiAuthorizationMatrixCompletenessTest` — no orphan write, no stale rule).
- **Closed hole:** the blanket `permitAll` on `/api/integration/**` left the **AFD upload** and the
  **crawl trigger** reachable with **no credentials**; only the 2 HMAC-signed webhooks remain M2M
  and the point endpoints now require **IT**.
- **Sensitive reads** gated too: People/Time-clock personal data → IT (LGPD); vault document
  **content** download excludes Viewer; Platform surface → IT/Director.
- Gates green: backend `./mvnw verify` **523 tests** (+10), ArchUnit/Modulith/Spotless/Checkstyle/
  JaCoCo unchanged; the accounts E2E journey now signs in as `ops` (the owning desk).

---

## 0.32.0 — Phase 18d · reference enums → cadastros (Finance/Payout/People/CommercialPolicy/AfterSales) — CLOSES Phase 18

**MINOR — new capability. No existing `/api` contract changed (the converted fields keep their `string`
schema).**

Slice 18d — the **last** of Phase 18 — reuses the `cadastro` module (18a) and the label pipe (18b) and
converts the five remaining reference-enum groups to validated `code`s (code = old enum constant name ⇒
JSON identical), rendering the cadastro **label** on the Finance/Payout/People/CommercialPolicy/AfterSales
screens. **With 18d, every business reference enum is now an editable cadastro — Phase 18 is complete.**
SPEC-0031 updated; ADR-0019 + DL-0118.

- **Finance:** `EntryType`, `PartyType` → validated codes (`ENTRY_TYPE`, `PARTY_TYPE`), validated on the
  ledger `register`. The AP/AR posting nature and the Compliance kind→document map (DL-0012) are preserved
  via `EntryTypeCodes`/`PartyTypeCodes` (internal producers emit the constants).
- **Payout:** `PayeeType`, `PayoutKind` → codes (`PAYEE_TYPE`, `PAYOUT_KIND`), validated on `create`. The
  settlement/repass/refund fact (`publishExecuted` switch) and the **merchant trap** (a REFUND that never
  nets the supplier obligation — DL-0024/DL-0051) are preserved via `PayoutKindCodes`.
- **People:** `DiscrepancyKind` → `code` (`DISCREPANCY_KIND`). **System-produced** by the
  `JourneyCalculator`, so it becomes a cadastro for the editable label — no write validation.
- **CommercialPolicy:** `ParameterValueType` → `code` (`PARAMETER_VALUE_TYPE`), validated on `defineRule`.
  The value-text parse/validation (NUMBER/PERCENT/MONEY/BOOL — DL-0037) is preserved via
  `ParameterValueTypeCodes`. `ParameterLayer` (precedence) **stays an enum** (documented keep — DL-0118).
- **AfterSales:** `SupportCaseType`, `CaseResolution` → codes (`SUPPORT_CASE_TYPE`, `CASE_RESOLUTION`),
  validated on `open`/`resolve`. The governed SLA selection (48h/72h — DL-0052) and the orchestration
  (REFUND_APPROVED → Payout REFUND; CANCEL_APPROVED → Booking cancel — DL-0054) are preserved via
  `SupportCaseTypeCodes`/`CaseResolutionCodes`.

**Border decisions (aggressive criterion):** `LedgerDirection` (binary accounting axis) and
`ParameterLayer` (fixed precedence hierarchy whose ordinal drives ordering/authorization) **stay enums**
(documented keep — DL-0118); `ParameterValueType` **is converted** (its behavior lives in the `*Codes`).

**Migration V36** seeds the 8 types (35 items), idempotent. **Gates:** backend `./mvnw verify` green —
**513 tests** (507 → 513), ArchUnit + Spring Modulith (23 modules, acyclic) + Spotless/Checkstyle/JaCoCo
all green, no gate weakened; frontend green — lint (0), **284 tests**, coverage above thresholds, build
OK; E2E journeys still compile (`playwright test --list`, 24), authored/not executed in-sandbox. OpenAPI
+ pom → **0.32.0**.

---

## 0.31.0 — Phase 18c · reference enums → cadastros (Sourcing/Exchange/Booking/Compliance) + labels on screens

**MINOR — new capability. No existing `/api` contract changed (the converted fields keep their `string`
schema).**

Slice 18c reuses the `cadastro` module (18a) and the label pipe (18b) and converts four more
reference-enum groups to validated `code`s (code = old enum constant name ⇒ JSON identical), rendering
the cadastro **label** on the Sourcing/Exchange-desk/Booking(Cancellation)/Compliance screens.
SPEC-0031 updated; ADR-0019 + DL-0117.

- **Sourcing:** `OfferOrigin`, `IntegrationLevel` → validated codes (`OFFER_ORIGIN`,
  `INTEGRATION_LEVEL`), validated on `register`. The INTEGRATED quoting branch (DL-0018) keeps minting
  `INBOUND` via `IntegrationLevelCodes`.
- **Exchange:** `MarketRateSource` → `code` (`MARKET_RATE_SOURCE`). **System-produced** (the contingency
  controller records `MANUAL`), so it becomes a cadastro for the editable label — no write validation.
- **Booking:** `ChargeKind`, `CancellationType` → codes (`CHARGE_KIND`, `CANCELLATION_TYPE`),
  `CancellationType` validated on the policy PUT. The **merchant trap** (ALL_SALES_FINAL supplier cost
  and customer refund that never net out) and the **penalty windows** are preserved via
  `CancellationTypeCodes`/`ChargeKindCodes` (DL-0024/DL-0010).
- **Compliance:** `DocumentType`, `SignedFormat`, `RequirementPhase` → codes, `DocumentType` validated
  on `upload`. The **legal retention** (FISCAL 5y / CONTRACT 10y) and the `AT_REGISTRATION` **close-check**
  (DL-0012) are preserved. `SignedFormat` is produced by the ingesting adapter — no write validation.
- **Added:** `*Codes` constants (`OfferOriginCodes`/`IntegrationLevelCodes`, `MarketRateSourceCodes`,
  `ChargeKindCodes`/`CancellationTypeCodes`, `DocumentTypeCodes`/`SignedFormatCodes`/
  `RequirementPhaseCodes`); the label pipe wired into the four Phase-16 screens (+8 frontend types).
  **Migration V35** seeds the 8 new types (37 items).
- **Changed:** the Sourcing/Exchange/Booking/Compliance enum fields became validated string codes (the
  enums were removed); the Finance `BookingChargeEventsListener` switch became a String switch with a
  safe default; `LegalTimeRecordArchived` no longer imports `compliance.DocumentType`. OpenAPI → 0.31.0.
- **Gates green:** backend `./mvnw verify` **507 tests** (was 503; round-trip, invalid/inactive code
  rejection, retention branch preserved), ArchUnit/Modulith(23)/Spotless/Checkstyle/JaCoCo unchanged;
  the merchant-trap and INTEGRATED-branch regressions stay green; frontend lint + **284 tests** + build;
  E2E `cadastro.spec.ts` extended with the 18c sourcing round-trip (authored + compiled; `playwright
  test --list`, 24 tests), not executed in-sandbox (infra).

---

## 0.30.0 — Phase 18b · reference enums → cadastros (Marketing/Intelligence/Portfolio) + labels on screens

**MINOR — new capability. No existing `/api` contract changed (the converted fields keep their `string`
schema).**

Slice 18b reuses the `cadastro` module (18a) and converts three more reference-enum groups to
validated `code`s (code = old enum constant name ⇒ JSON identical), and retro-fixes the 18a seam by
rendering the cadastro **label** on screen instead of the code. SPEC-0031 updated; ADR-0019 + DL-0116.

- **Marketing:** `ConsentPurpose`, `SubjectType` → validated codes (`CONSENT_PURPOSE`,
  `MARKETING_SUBJECT_TYPE`), validated on write (`grantConsent`).
- **Intelligence:** `SubjectKind`, `InsightType`, `Verdict` → codes. These are **system-produced** (the
  DSS mints them from consumed events), so they become cadastros for the editable label — no write
  validation.
- **Portfolio:** `GoalMetric` → `code`, validated on `defineGoal`. The realized-projection branching is
  preserved: VOLUME←BookingConfirmed, REVENUE←SpreadRealized (DL-0062).
- **Added:** `*Codes` constants (`MarketingCodes`, `IntelligenceCodes`, `GoalMetricCodes`); a frontend
  label lookup — `CadastroLabelService` (cache-first per-type `code→label` fetch) + `CadastroLabelPipe`
  — wired into the Marketing/Intelligence/Portfolio screens; `marketing.purpose` i18n (pt-BR + en).
  **Migration V34** seeds the 6 new types.
- **Changed:** the Marketing/Intelligence/Portfolio enum fields became validated string codes (the
  enums were removed); those screens now render the label. OpenAPI → 0.30.0.
- **Gates green:** backend `./mvnw verify` **503 tests** (was 495; round-trip, invalid/inactive code
  rejection, branching preserved — REVENUE/VOLUME projection, verdict narrator), ArchUnit/Modulith/
  Spotless/Checkstyle/JaCoCo unchanged; frontend lint + **284 tests** + build; E2E `cadastro.spec.ts`
  extended with the 18b goal-metric round-trip (authored + compiled; `playwright test --list`, 23
  tests), not executed in-sandbox (infra).

---

## 0.29.0 — Phase 18a · `cadastro` module + reference enums → editable cadastros (Admin/Assets/Billing)

**MINOR — new capability. No existing `/api` contract changed (the converted fields keep their `string`
schema).**

Per the owner's decision that **every reference enum that is neither a state machine nor law-fixed
becomes an editable cadastro** (lookup data with a pt-BR label, order and active flag — no redeploy),
slice 18a ships the mechanism and converts the first group (Admin/Assets/Billing). New **SPEC-0031**;
ADR-0019 + DL-0115.

- **New `cadastro` module (23rd Modulith, a leaf):** a single `cadastro_item(type, code, label, active,
  sort_order, …)` registry (`unique(type, code)`), `CadastroService` CRUD (code immutable; label/active/
  order editable) and the public `CadastroValidator` port other modules call to validate a code.
- **The invariant (wire contract unchanged):** each `@Enumerated` field becomes a validated `String
  code` whose value equals the old enum constant name (`"UTILITY"`, `"SIMPLES_NACIONAL"`, …), so
  request/response JSON is identical; a brand-new code with no wired logic works as pure data.
- **Branching preserved** via `*Codes` constants: `AdminExpenseCodes.entryTypeFor` (kind→EntryType→
  document, DL-0085), `TaxRegimeCodes` (regime→tax strategy, DL-0044), `WithholdingKindCodes` (codec),
  `AssetCodes.isSoftwareLicense` (requires expiry).
- **Added:** `GET /api/cadastro/types`, `GET /api/cadastro/items?type=…` (reads — authenticated);
  `POST`/`PUT`/`DELETE /api/cadastro/items(/{id})` (writes — `ROLE_POLICY_ADMIN`, an existing role; no
  new role, auth untouched). A **"Reference data" screen** in the shell (nav + route gated by the
  role). **Migration V33** creates and seeds `cadastro_item`.
- **Changed:** the Admin/Assets/Billing enums (`AdminExpenseKind`/`AdminRecurrence`/`AdminSupplierType`,
  `AssetType`, `WithholdingKind`, `TaxRegime`) became validated string codes; the enums were removed.
  OpenAPI → 0.29.0.
- **Gates green:** backend `./mvnw verify` **495 tests** (was 480), ArchUnit/Modulith (23 modules,
  acyclic)/Spotless/Checkstyle/JaCoCo unchanged; frontend lint + **278 tests** + build; the
  `cadastro` E2E journey (3 tests) is authored and compiles (`playwright test --list`), not executed in
  this sandbox.
- **Next (18b–18d):** convert the remaining enum groups reusing this module (`0.30.0`–`0.32.0`).

---

## 0.28.0 — Phase 17 · Remove Keycloak → self-hosted Authorization Server (embedded Spring Authorization Server)

**MINOR — new capability + IdP swap. BREAKING at the infra/config level (allowed in `0.y`, ADR 0015 §4):
Keycloak is removed. No `/api` contract changed.**

Per the owner's decision to **not use Keycloak**, Phase 17 removes it 100% and serves OIDC from the
**Spring Authorization Server embedded in the app** (no extra process/Docker). Only the IdP was swapped:
the app is now **both the IdP and the Resource Server**. Because the access token keeps the same
`realm_access.roles` claim Keycloak used to emit, the Resource Server, the role mapping, the
`UserContextProvider` port and the **480 backend tests** are **unchanged** — only the token's *origin*
moved (to the app itself). Re-graduates **SPEC-0024**; ADR-0018 + DL-0110..0114.

- **Breaking (ops/config):** the `keycloak` service is gone from `docker-compose.yml` and
  `compose.e2e.yaml`; `infra/keycloak/` (realm export + README) is deleted; `KEYCLOAK_*` vars are gone
  from `.env.example`; `OIDC_ISSUER_URI` now points at the app itself (`:8080` dev, `:8081` E2E). The
  in-house `POST /api/identity/login` stays removed (retired in Phase 13).
- **Added — embedded Authorization Server** (`infra/security/AuthorizationServerConfig`): three
  `SecurityFilterChain`s (AS `@Order(1)`, Resource Server `/api/**` `@Order(2)`, form login `/login`
  `@Order(3)`). Serves `/.well-known/openid-configuration`, `/oauth2/authorize|token|jwks`, `/userinfo`
  and a form `/login`; signs RS256 with a local RSA key. An `OAuth2TokenCustomizer` adds
  `realm_access.roles` + `preferred_username` to the access token (DL-0110).
- **Added — public SPA client** `acme-erp-web` (Authorization Code + PKCE, no secret, no consent screen),
  mirroring the old realm client (DL-0111).
- **Added — local user store reintroduced** (migration **V32**, idempotent): re-creates `identity_users`
  + `user_roles` (BCrypt) with a `UserDetailsService` and a dev/E2E seeder (`dev` + one per role,
  `dev12345`, dev/E2E only). The role→permission catalogue stays intact (DL-0112).
- **Changed — Resource Server** re-pointed at the app's own issuer/JWKS; authorization behavior unchanged.
- **Changed — frontend** issuer → the app itself; silent-refresh via a hidden **iframe** (the AS issues
  no refresh token to a public client — DL-0113); `angular-oauth2-oidc`, interceptor, guard and the
  `AuthService` public API are unchanged. New `public/silent-refresh.html`.
- **Changed — E2E** re-authored against the self-hosted AS (login via the app's `/login`; role-check
  tokens obtained through the real code+PKCE browser flow). 19 journeys compile (`playwright test --list`).
- **Gates:** backend `./mvnw verify` GREEN (480 tests; ArchUnit/Modulith/Spotless/Checkstyle/JaCoCo all
  pass; a new `AuthorizationServerIntegrationTest` boots the real AS). Frontend GREEN (lint; 264 tests;
  coverage above thresholds; production build). E2E authored + compiled, not executed in the sandbox.

## 0.27.0 — Phase 16d · Operator screens: People/HR, Time clock, Assets, Back-office, Platform/IT and Access (SPEC-0029 — closes Phase 16)

**MINOR — new user-facing capability, retro-compatible (ADR 0015). Frontend-only over existing APIs;
no new endpoint, no contract/schema change.**

Phase 16d pays off the **remaining UI gap** (DL-0109), now for the **back-office and IT**. It is the
**last of the four Phase 16 slices**: with it, **every module that only existed as an API now has an
operator screen** — the operator sees the whole ERP. It extends **SPEC-0029** and delivers six more
operator screens, reusing the established frontend pattern.

- **Added — People/HR screen** (`/people`): **collaborators** (list by status + register); **journey and
  time-bank** for a collaborator/period (worked × contracted × balance — all from the backend, no client
  math); and the **discrepancy queue** (period/status filters — an alert, no auto-correction). Consumes
  `GET/POST /api/people/employees`, `GET /employees/{id}/journey`, `GET /employees/{id}/timebank`,
  `GET /discrepancies`. The payslip stays archived in the vault by the existing multipart flow.
- **Added — Time-clock screen** (`/point`): the REP **crawl-run history** (status filter, with
  attempts/items/failure-class) and an **operational snapshot** by id — operator/IT reads. Consumes
  `GET /api/integration/point/runs` and `GET /snapshots/{id}`. The signed AFD/AEJ ingest (`POST /afd`) and
  the crawl trigger (`POST /crawl`) are machine-to-machine/operational contracts and get **no screen**.
- **Added — Assets screen** (`/assets`): **list** with combinable type/status/expiring filters, **register**
  (cost in the original currency), **retire** with an audited reason and the **license-expiry sweep** (shows
  the flagged count). Consumes `GET/POST /api/assets`, `GET /{id}`, `POST /{id}/retire`, `POST
  /flag-expiring`.
- **Added — Back-office (Admin) screen** (`/admin`): **administrative suppliers** (list/register),
  **contracts** per supplier (list/register), a **recurring expense** (register — creates the Finance entry
  and lists the required documents) and the **contract-expiry sweep**. Consumes `GET/POST
  /api/admin/suppliers`, `GET/POST /suppliers/{id}/contracts`, `POST /expenses`, `POST
  /contracts/flag-expiring`. **Writes require `ROLE_FINANCE`** (DL-0088) — without it, a 403 renders as the
  permission state.
- **Added — Platform/IT screen** (`/platform`): the **governed-job catalog** + **run history** (job/status
  filters) + **manual trigger** (ROLE_IT; 404 unknown, 409 already running); the **e-CNPJ certificate
  status** — **metadata only** (subject/holder/fingerprint/validity/days/status); the **key and password
  NEVER reach the UI**; and the consolidated **system audit** (actor/type/window filters). Consumes
  `GET /api/platform/jobs`, `GET /jobs/runs`, `POST /jobs/{name}/trigger`, `GET /certificate/status`,
  `GET /audit`.
- **Added — Identity/Access screen** (`/identity`): the **role/permission catalogue** (source of truth of
  internal authorization) and the **access audit** (login/denial; actor/type/window filters). Both require
  **DIRECTOR or IT** — without the role, a 403 (permission state). Consumes `GET /api/identity/roles` and
  `GET /access-audit`. Login is at the external OIDC IdP (Phase 13); no credential management here.
- **Added — navigation & i18n:** six new nav items — People/Time-clock/Assets/Platform
  `roles: ['ROLE_IT']` (**there is no "HR" role** in the realm), Admin `['ROLE_FINANCE']`, Identity
  `['ROLE_DIRECTOR','ROLE_IT']`; pt-BR and en-US i18n blocks per screen. Screens whose first letter clashes
  with an earlier route remain reachable via the `Ctrl/Cmd+K` palette.
- **Tests:** Vitest per screen + service specs (`HttpTestingController`) → **264 frontend tests** (49
  files), coverage above the Phase-12 floors; Playwright journey `e2e/platform-people.spec.ts` (IT opens
  Platform → metadata-only certificate + job catalog, then People → empty list; a token without ROLE_IT is
  denied 403 on the governed-job trigger, IT is allowed — authority in the backend).
- **Changed — `backend/pom.xml` + OpenAPI**: version `0.26.0 → 0.27.0` (only the version string and the
  OpenAPI description text, which now records 16c/16d as frontend-only and Phase 16 as complete). `./mvnw
  verify` stays **green — 476 tests, BUILD SUCCESS** (no backend behavior change).
- **Not changed (frontend-only):** no new endpoint, no migration, no contract/JSON/event/schema change.
- **E2E in the sandbox:** the Playwright journey is **authored and compiles** (Playwright discovers it — 19
  tests via `npx playwright test --list`) but was **not executed here** because building the isolated
  stack's backend image needs Maven network/cache inside the container, unavailable in the sandbox. `./mvnw
  verify` on the host is green — an infra limit, not a code defect.
- **Phase 16 complete:** no operator-UI slices remain; the whole UI debt (DL-0109) is paid off.

---

## 0.26.0 — Phase 16c · Operator screens: Intelligence/DSS, Commercial policy, Marketing and Portfolio (SPEC-0029)

**MINOR — new user-facing capability, retro-compatible (ADR 0015). Frontend-only over existing APIs;
no new endpoint, no contract/schema change.**

Phase 16c continues paying off the **UI gap** (DL-0109), now for **Intelligence & Growth**. It extends
**SPEC-0029** and delivers four more operator screens, reusing the established frontend pattern.

- **Added — Intelligence screen** (`/intelligence`): lists the DSS **insights** filtered by
  type/subject/status (ordered by estimated gain); opens one to read its **evidence** (accrued subsidy,
  realized gap, attracted volume, provenance), **recommendation** (CONVERTE/QUEIMA_MARGEM verdict, action,
  gain/risk) and the crossed **guardrail** (an alert, never a block); and **records the human decision**
  (accept/reject/dismiss + note). Recording a decision **only records it — it never triggers an action**
  (the DSS advises, the human decides). Consumes `GET /api/intelligence/insights`, `GET /{id}`,
  `POST /{id}/decision`.
- **Added — Commercial-policy screen** (`/commercial-policy`): **resolves** a governed parameter by scope
  (key + account/product/channel) showing the **winning value and provenance** (which layer won,
  who/when); lists the **rules** for audit with the fixed **precedence** Directive > Promotion > Contract
  > Policy > Default; **defines a rule** (POLICY/PROMOTION/CONTRACT — curator/director) and **issues a
  directive** (top of precedence; mandatory justification; director role). Consumes
  `GET /api/commercial-policy/resolve`, `GET/POST /rules`, `POST /directives`.
- **Added — Marketing screen** (`/marketing`): **LGPD consent** (current state + append-only history;
  grant/revoke); **segments** (define + preview reach); **campaigns** (create + dispatch — the dispatch
  filters by consent and reports targeted/suppressed/queued); **attribution** campaign→booking (register
  + list); and **LGPD erasure** (removes PII, keeps the revocation tombstone). Consumes the 11
  `/api/marketing` endpoints.
- **Added — Portfolio screen** (`/portfolio`): **represented brands** (list/register/deactivate);
  **representation contracts** (list + coverage on a date — an alert, never a block; register); and
  **goals × realized** (define a VOLUME/REVENUE goal + progress target × realized × attainment). Consumes
  the 11 `/api/portfolio` endpoints.
- **Added — Navigation & i18n:** four new nav items (Intelligence/Marketing/Portfolio gated to
  `ROLE_OPERATIONS`; Commercial policy to `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN`); pt-BR and en-US i18n
  blocks per screen.
- **Added — Tests:** Vitest per screen + service specs (`HttpTestingController`) → **196 frontend tests**,
  coverage above the Phase-12 floors; Playwright journey `e2e/intelligence-policy.spec.ts` (a director
  opens Intelligence → empty list → resolves a parameter in Commercial policy; a token without
  ROLE_DIRECTOR is 403 on the directive endpoint, the director is allowed — backend authority).
- **Changed — `backend/pom.xml` + OpenAPI:** version `0.25.0 → 0.26.0` (version string + OpenAPI
  description text only). `./mvnw verify` stays **green — 476 tests, 0 Checkstyle violations** (no backend
  behavior change).
- **Not changed (frontend-only):** no new endpoint, no migration, no contract/JSON/event/schema change.
  The screens compose existing endpoints (the dashboard pattern — DL-0094).
- **Note — E2E authored, not executed here:** the Playwright journey compiles and is discovered (17 tests
  via `npx playwright test --list`) but was not run in the sandbox because the isolated stack builds the
  backend image via Maven inside the container (no artifact network). Host `./mvnw verify` is green — an
  infra limitation, not a code defect.

---

## 0.25.0 — Phase 16b · Operator screens: After-sales, Sourcing, FX desk and Cancellation (SPEC-0029)

**MINOR — new user-facing capability, retro-compatible (ADR 0015). Frontend-only over existing APIs;
no new endpoint, no contract/schema change.**

Phase 16b continues paying off the **UI gap** (DL-0109), now for the **commercial cycle**. It extends
**SPEC-0029** and delivers four more operator screens, reusing the established frontend pattern. The
existing **pinned-rate Exchange screen stays intact** — the FX desk is a **companion** screen, not a
replacement.

- **Added — After-sales screen** (`/aftersales`): cases filtered by type/status/booking/**SLA breach**,
  **open** a case, drive the **state machine** (assign/wait/close) and **resolve** it (approve refund →
  Payout REFUND; approve cancellation → Booking cancellation; resolve-no-action; reject). The `breached`
  flag is an orthogonal alert that **never blocks** (SPEC-0018 BR4). Consumes `GET/POST
  /api/aftersales/cases`, `GET /{id}`, `POST /{id}/{assign|progress|wait|close}`, `POST /{id}/resolve`.
- **Added — Sourcing screen** (`/sourcing`): **register** an offer's provenance (product text, base
  price, origin, integration level, external ref) and **look up** an offer by id. Consumes `POST
  /api/sourcing/offers`, `GET /api/sourcing/offers/{id}`.
- **Added — FX desk screen** (`/exchange-desk`): the book's **live exposure** (accrued subsidy +
  mark-to-market drift + **drift alert**), the **market rate** (manual contingency record + history), a
  booking's **position** (subsidy × drift) and the **PromoFx report** per period. Consumes `GET
  /api/exchange/exposure`, `GET/POST /api/exchange/market-rates` (+`/current`),
  `GET /api/exchange/positions/{bookingId}`, `GET /api/exchange/reports/promo-fx`.
- **Added — Cancellation screen** (`/cancellation`): **look up and administer** the per product/supplier
  policy — type, **penalty windows**, refundable, cost bearer, merchant of record, no-show fee; surfaces
  the **merchant trap** (ALL_SALES_FINAL ⇒ not refundable to the supplier). Consumes `GET/PUT
  /api/products/{ref}/cancellation-policy`.
- **Added — navigation & i18n:** five new nav items (After-sales/Sourcing/FX desk/Cancellation gated on
  `ROLE_OPERATIONS` — menu tidiness only, the backend stays the authority); pt-BR and en i18n blocks per
  screen. Fixes the `g`+key shortcut map to keep the first screen when two paths share an initial
  (accounts/aftersales, exchange/exchange-desk, compliance/cancellation).
- **Added — tests:** Vitest per screen + `HttpTestingController` service specs (**135 frontend tests**,
  coverage above the Phase-12 floors) and a Playwright After-sales/Cancellation journey
  (`e2e/aftersales-cancellation.spec.ts`): an OPERATIONS user opens After-sales → empty list → looks up a
  Cancellation policy; a non-FINANCE token is denied 403 on the finance close (backend authority).
- **Changed:** backend `pom.xml` + OpenAPI version `0.24.0 → 0.25.0` (version string + OpenAPI
  description text only; `./mvnw verify` stays green — **476 tests, 0 Checkstyle violations**). No new
  endpoint, no migration, no contract change.

## 0.24.0 — Phase 16a · Operator screens: Finance, Billing, Payouts and Compliance (SPEC-0029)

**MINOR — new user-facing capability, retro-compatible (ADR 0015). Frontend-only over existing APIs;
no new endpoint, no contract/schema change.**

Phase 16a starts paying off the **UI gap** diagnosed in **DL-0109** (the backend has 22 modules / 37
REST controllers, but the frontend only had screens for ~5 of them). It authors **SPEC-0029** and
delivers the first four operator screens, reusing the established frontend pattern (feature service +
`API_BASE_URL` + `PageResponse`; `<app-screen-state>` for loading/empty/error/permission; lazy route
under the Shell with guards; role-gated nav; bilingual i18n; Vitest + Playwright).

- **Added — Finance screen** (`/finance`): the AP/AR ledger filtered by direction/status/period/party,
  a new-entry form, a period lookup with its **per-currency trial balance** (DL-0013 — currencies are
  never summed together) and the **monthly close** (which surfaces `finance.period.cannot-close` when
  Compliance vetoes it). Consumes `GET/POST /api/finance/entries`, `GET /api/finance/periods/{yyyymm}`,
  `.../trial-balance`, `POST .../close`.
- **Added — Billing screen** (`/billing`): look up a commission invoice by id (base/ISS/withholdings/
  status/number), create a draft, **issue** (ROLE_FINANCE) and **cancel**. Consumes `GET/POST
  /api/billing/invoices`, `POST .../issue`, `POST .../cancel`.
- **Added — Payouts screen** (`/payouts`): list repasses/settlements/refunds filtered by kind/status/
  payee, open one with its installments, create and **execute** (an explicit FAILED on provider failure,
  never a false EXECUTED). Consumes `GET/POST /api/payouts`, `GET /{id}`, `POST /{id}/execute`.
- **Added — Compliance screen** (`/compliance`): run a period **close-check** (may-close / pending
  entries and what each is missing), **upload** a document to the vault and **look up** a document by id
  (type/hash/issue/retention/personal-data; the internal fileRef is never exposed). Consumes `GET
  /api/compliance/close-check`, `GET/POST /api/compliance/documents`.
- **Added — navigation & i18n:** four nav items (Finance/Billing/Payouts gated on `ROLE_FINANCE`;
  Compliance visible to any authenticated user — the backend stays the authorization authority, this
  only hides menu noise); pt-BR and en i18n blocks per screen.
- **Added — tests:** a Vitest spec per screen (89 frontend tests total, coverage above the Phase-12
  floors) and a Playwright Finance/Compliance journey (`e2e/finance.spec.ts`): a FINANCE user opens
  Finance → empty ledger → Compliance close-check; a non-FINANCE token is denied 403 on the finance
  close while a FINANCE token passes the gate.
- **Changed:** backend `pom.xml` + OpenAPI version `0.23.1 → 0.24.0` (version string only; `./mvnw
  verify` stays green — **476 tests, 0 Checkstyle violations**). No new endpoint, no migration, no
  contract change.

## 0.23.1 — Phase 14 · Stack upgrade to Spring Boot 4.0.7 (internal maintenance, no user-facing change)

**PATCH — infrastructure maintenance, no contract change (REST/DTO/wire-JSON/published-event/i18n/schema
unchanged) and no new user-facing capability (Rule Zero).**

The backend moves from **Spring Boot 3.5.16 to 4.0.7** (Spring Framework 7, Jakarta EE 11), with **Spring
Modulith 2.0.7** and **springdoc-openapi 3.0.3**. Java stays **21 (LTS)**, Testcontainers **1.21.4**,
Postgres **16-alpine**. The `DL-0002` Phase-0 version pin is **superseded** by this phase (ADR 0017).

- **Changed:** Spring Boot parent `3.5.16 → 4.0.7`; Spring Modulith `1.4.12 → 2.0.7`; springdoc
  `2.8.17 → 3.0.3`; the Testcontainers BOM is now imported explicitly (Boot 4 no longer manages it).
- **Added (compatibility):** `spring-boot-starter-classic` keeps the pre-4.0 classpath (Jackson 2 default),
  so the 22 production Jackson-2 uses (jsonb codecs, integration/security adapters) stay unchanged —
  **production serialization is identical, no contract change**. Full Jackson-3 migration is tracked debt
  (DL-0108). Test deps `spring-boot-resttestclient`, `spring-boot-restclient` and
  `spring-boot-micrometer-metrics-test` were added for Boot-4 test support.
- **Test-only fixes (behaviour preserved):** `TestRestTemplate` relocated +
  `@AutoConfigureTestRestTemplate` on the base class (41 ITs); `@AutoConfigureObservability` →
  `@AutoConfigureMetrics` (2 metrics ITs); test `JsonNode` reads → `tools.jackson.databind.JsonNode`
  (8 ITs); the Spring Framework 7 status rename `UNPROCESSABLE_ENTITY → UNPROCESSABLE_CONTENT` (3 ITs +
  1 production mapping). **HTTP 422 stays 422 on the wire.**
- **Not done (Rule Zero):** `@swimlane/ngx-graph` was not adopted (no configurable-workflow requirement);
  the production Jackson-3 migration and `RestTestClient` adoption are deferred (DL-0108).
- **Decisions:** **ADR 0017** (the upgrade), **DL-0108** (sub-decisions), **DL-0002** updated (superseded).
- **Verified:** backend `./mvnw verify` green on Spring Boot 4.0.7 — **537 tests, 0 failures** (same count
  as 0.23.0), JaCoCo ≈ 89.7 % (floor 0.80), Spotless/Checkstyle/ArchUnit/Spring Modulith (22 modules)
  green; frontend lint/test (56)/build green. No gate weakened. The MANUAL is unchanged (Rule Zero).
- This is the **last numbered ROADMAP phase** (Phase 15 is the standing bilingual-docs rule/chore).

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
