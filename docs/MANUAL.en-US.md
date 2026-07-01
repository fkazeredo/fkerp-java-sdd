# Instruction Manual — Acme Travel ERP

> Manual in **English**, for the end user/operator (non-technical). Describes **what the system
> already does today**. Updated **on every delivered slice** (see the *User manual* command in
> `CLAUDE.md`). Portuguese version: `docs/MANUAL.md` (kept in sync).
>
> **System version:** 0.22.0 · **Current phase:** 11 (Observability & monitoring)

## 1. What the system is

The **Acme Travel ERP** is the commercial and financial management system of Acme Travel (a
representative of travel brands — a GSA). When complete, it will handle exchange rates, commissions,
quotes, bookings, reconciliation and the tax documents of sales.

Business capabilities are delivered **one vertical slice at a time**. This manual focuses on the
slices that already have a **screen/journey for the user**; internal capabilities appear here as they
gain direct operator use (see the *Manual version history* at the end).

## 2. How to access

> There is no installer yet; the system is started from the command line. You need **Docker** and
> **Node.js** installed. The steps below are simple and explained.

**Step 1 — Start the server and the database.** In the project folder, run:

```
docker compose up --build
```

This starts the database and the application server. When it shows "started", the server is ready at
`http://localhost:8080`.

**Step 2 — Open the screen.** In another terminal, in the `frontend` folder, run `npm install` once
and then:

```
npm start
```

Open the browser at **`http://localhost:4200`**.

**To stop:** close `npm start` and run `docker compose down` in the project folder.

## 3. Available features

### Phase 0 — "System health" screen

This is the only screen of this phase. When you open `http://localhost:4200`, the system
automatically checks whether everything is up and shows one of these states:

| State | What you see | What it means |
|---|---|---|
| Checking | **"Checking…"** | The system is verifying whether everything is up (a few moments). |
| Healthy | **"Service is healthy"** + Status: `UP` and Database: `UP` (green card) | Application and database are working. |
| Error | **"Could not reach the backend"** + **"Retry"** button (red card) | The server is down or unreachable. Check that `docker compose up` is running and click **Retry**. |

> For IT: the same check is available at `GET http://localhost:8080/api/system/health`, which
> answers `{ "status": "UP", "db": "UP" }`.

### Phase 3 — Offer provenance (where the sale came from)

From this phase on, the system records **where an offer came from** — what the redesign calls
*Sourcing*. This applies both to a manually typed sale (a price from an external website, a paper
catalog or a phone order) and to the quote that arrives automatically from the **quotation site**
(see below).

**Registering an offer manually.** The operator can record an offer's provenance by entering:

- **Product description** (free text — for example, "City Tour Rio - full day"). It does not need to
  be in a structured catalog: free text is a valid offer.
- **Base price** (amount and currency).
- **Origin:** Own portal, External website, Third-party catalog, or Raw demand (one-off request).
- **Integration level:** None, Inbound (the external system feeds the ERP) or Two-way.
- **External reference** (optional): for example, the quote number on the source site.

> For IT: `POST /api/sourcing/offers` records the offer and `GET /api/sourcing/offers/{id}` reads it.
> It is a **traceability** record (where the sale came from); it does not compute price.

**Automatic quote from the quotation site (integrated quote).** When the **quotation site** sends a
quote to the system, the ERP automatically creates an **integrated quote**: the incoming price is a
**closed, trusted price**, so the system **does not recompute** exchange, commission or margin — it
just records the quote with that price. What the operator needs to know:

- The quote appears with origin **INTEGRATED** and the **applied amount is exactly the received
  price** (no suggestion, no manual adjustment).
- The system only accepts the quote if the message is **signed** (a security key agreed with the
  site). A message without a signature, or with a wrong one, is **rejected** and **nothing is
  created**.
- If the **same** quote number arrives again, the system **does not duplicate**: it returns the
  quote already created.
- The quote must be linked to an **already-registered commercial account** (by its document). If the
  document matches no account, the quote is **rejected** (register the account first).

> For IT: the site calls `POST /api/integration/quotation-site/inbound` with the `X-Signature`
> header. The connector's health is at `GET /api/integration/quotation-site/health`.

### Phase 4 — Cancellation policy, penalties and the "merchant trap"

From this phase on, a cancellation is no longer "undo everything, no penalty" and instead follows a
**cancellation policy** configurable per product/supplier. The policy states: which type it is
(Standard, Final sale, or Custom), the **penalty windows** (how much is charged depending on how
close the service is), whether it is refundable, **who pays** the penalty, and whether the sale is
"merchant" (the portal takes on the charge/refund) or affiliate.

**Configuring a product's policy.** An administrative user enters, for a product/supplier:

- **Type:** *Standard* (penalty by window), *Final sale* (`ALL_SALES_FINAL` — non-refundable from
  the supplier's standpoint) or *Custom*.
- **Penalty windows:** pairs of "up to how many hours before the service" → "penalty percentage" (for
  example: up to 24h before, 50%; up to 72h before, 25%). Cancelled earlier than all windows? No
  penalty.
- **Who pays** the penalty: the agency, Acme or the supplier.
- **Merchant sale?** If yes, Acme takes on the obligation to the supplier and any refund to the
  customer. If not (affiliate — the default), the supplier takes it on.
- **No-show penalty** (car) and whether it is **waived with proof of a cancelled flight**.

> For IT: `PUT /api/products/{ref}/cancellation-policy` saves and
> `GET /api/products/{ref}/cancellation-policy` reads. A product with no configured policy uses a
> **safe default** (Standard, affiliate, no windows, no penalty).

**Cancelling a booking.** When cancelling, the operator enters the **reason**, **when the service
starts** and, if any, **a refund to the customer**. The system uses the policy **frozen at booking
confirmation** (not the current policy — so changing the policy later does not alter already-confirmed
bookings) and returns the list of **charges** generated:

- **Penalty** (on Standard/Custom sales), per the window and with who pays.
- On a **Final sale**: the **cost with the supplier** remains **fully due**, even if you decide to
  **refund the customer**. These are **two separate obligations that do not cancel each other out** —
  this is the **"merchant trap"**: treating them as one (netting one against the other) would hide
  lost money. The system keeps both visible.

**Recording a no-show (car).** If the customer does not show up, the system charges the policy's
**no-show penalty**. If the policy allows a **waiver with proof of a cancelled flight** and the
operator provides the proof, the penalty is **waived**.

> For IT: `POST /api/bookings/{id}/cancel` now accepts `{reason, serviceStartsAt, refundAmount}` and
> returns the charges; `POST /api/bookings/{id}/no-show` accepts `{flightCancelledProof}`. Formal
> verification of the proof document is the document vault's responsibility (Compliance), in a later
> phase.

### Finance — Accounts Payable/Receivable and the monthly close

The system keeps Acme's **cash book**: the record of what the company **owes** (Accounts Payable) and
what it has **to receive** (Accounts Receivable), organized by **accounting month** (period
`YYYY-MM`).

**Posting a payable or a receivable.** The operator records an entry stating the direction (payable
or receivable), the party (supplier, agency/agent), the amount and currency, the entry type and the
month. The entry is born **provisional** (the document may still be missing). It can later be
**confirmed**.

**Closing the month — and the "golden rule".** When closing a period, the system **queries the
document vault (Compliance)**. If there is an entry without the required document, **the month does
not close**: the system reports which entries are pending and what is missing. With the documents
attached, the same period **closes**. This is the "no close without the invoice" point.

**Automatic entries from operations.** The operator does not have to post everything by hand: when a
booking generates a charge, the system **creates the entry by itself**, in the month the fact
happened:

- **Cancellation with penalty** → a **receivable** (the agency's penalty).
- **Refund to the customer** → a **payable** (the refund).
- **Supplier cost on a final (merchant) sale** → a **payable** to the supplier — which **coexists**
  with the refund to the customer, without netting out (the "merchant trap").
- **No-show with penalty** → a **receivable** (the no-show penalty).

Each operation becomes an entry **exactly once**, even if the internal notice arrives repeated —
there is no duplication.

**Viewing the month's trial balance.** At any time, the operator can consult a period's **operational
trial balance**: for **each currency** (without mixing currencies), how much is **payable**,
**receivable** and the **net** (receivable minus payable), plus how many entries are provisional,
confirmed or settled. It is a cash view of the month — not an accounting statement.

> For IT: `POST /api/finance/entries` (creates, returns `PROVISIONAL`),
> `POST /api/finance/entries/{id}/confirm`, `GET /api/finance/entries?...` (paginated list),
> `POST /api/finance/periods/{yyyy-mm}/close` (closes — `409 finance.period.cannot-close` with the
> pending items when Compliance vetoes), `GET /api/finance/periods/{yyyy-mm}` (status + AP/AR totals
> per currency) and `GET /api/finance/periods/{yyyy-mm}/trial-balance` (trial balance per currency
> with `payable`/`receivable`/`net` + counts by status). The automatic entries are created by
> consuming the bookings' cancellation/no-show events, idempotently.

### Phase 8 — After-sales (support cases, SLA, refund and cancellation)

**After-sales** records the **support cases** (complaint, change request, cancellation request,
refund request or information) tied to a booking and tracks the **service-level deadline (SLA)**.

What the operator does:

- **Open a case:** provide the booking, the type and a summary. The system computes the SLA deadlines
  from the **governed SLA rules** (default: **first response in 24h**, resolution in **72h**, and
  **48h** for cancellation/refund). Those deadlines can be tightened by a **director directive**
  (no new system release needed).
- **Drive the case:** assign, progress, put on hold, then **resolve** and **close**. Reopening a
  resolved case is recorded (it counts towards the "cost to serve").
- **Resolve with a refund:** approving a refund **forwards the payment to the Payout module**
  referencing the case itself — **exactly once** (no duplicate refund). The customer refund does
  **not** erase the supplier obligation (the merchant trap is preserved).
- **Resolve with a cancellation:** approving a cancellation **drives the booking cancellation** (which
  applies the penalty policy); after-sales never changes the booking on its own.
- **SLA breached:** when the deadline passes without resolution, the case is **flagged as "SLA
  breached" (an alert)** — it does **not** block the work; it helps prioritize and measure the "cost
  to serve" per product/supplier.

> For IT: `POST /api/aftersales/cases` (opens — returns `OPEN` with `dueAt`),
> `POST /api/aftersales/cases/{id}/assign|progress|wait|resolve|close` (`resolve` may drive Booking
> and/or Payout), `GET /api/aftersales/cases/{id}` and
> `GET /api/aftersales/cases?type=&status=&bookingId=&breached=&page=&size=`. The SLA sweep runs as a
> job with a controllable clock; the refund is idempotent per case.

### Phase 8 — Marketing (LGPD consent, campaigns and attribution)

**Marketing** talks to the **B2B** base (agencies/agents) and treats **LGPD consent** as a mandatory
pre-condition: communication is **never** sent to someone who did not opt in. The module handles
consent, segmentation, newsletter dispatch and measuring **how much the campaign turned into sales**.
It is **not** a full CRM — it is the consent/attribution layer.

What the operator does:

- **Record consent:** for a subject (agency/agent), a purpose (e.g. newsletter) and a source (e.g. a
  sign-up form). Each decision is a **row that is never deleted**; the current state is always the
  subject's **latest** decision for that purpose.
- **Revoke consent:** revoking does **not** erase history — it is appended as a new decision. From then
  on, the subject is **excluded** from the next dispatches.
- **Look up consent:** the **current state** plus the full **history** for a subject and purpose.
- **Create a segment:** the audience defined by **criteria over already-existing data** (e.g. account
  type, region) — no new data collected. Criteria outside the allowed list are rejected.
- **Preview the reach:** the system shows how many **consented** subjects the segment would reach.
- **Create and send a campaign:** the campaign targets a segment and has its own **code**. On dispatch,
  the system sends **only to those who consented**; the others are **excluded and counted** (shown as
  "suppressed"); nobody receives the same campaign **twice**. Dispatch goes through an **external
  newsletter provider** (currently a traceable simulator).
- **Attribute a sale to a campaign:** records that a booking came from a **campaign code**. When that
  booking is **confirmed**, the system marks the **conversion** and sends that signal to **Intelligence
  (the DSS)** — that is how campaign return is measured.
- **Honour an erasure request (LGPD):** removes the subject's **marketing data** and **ends** consent,
  while **keeping the proof that they opted out** (so they are not re-included by mistake). What
  **another law requires to keep** (invoices, financial entries, the booking) is **not** erased here.

> For IT: `POST /api/marketing/consents`, `DELETE /consents/{id}` (revoke),
> `GET /consents?subject=&subjectType=&purpose=`; `POST /segments`, `GET /segments/{id}/preview`;
> `POST /campaigns`, `POST /campaigns/{id}/send` (returns `targeted/suppressedNoConsent/queued`),
> `GET /campaigns/{id}`; `POST /attribution`, `GET /attribution?campaignCode=`; `POST /erasure`.
> Consent is an append-only log (state = latest row); dispatch is idempotent per
> `(campaign, recipient)`; the newsletter provider is an ACL (traceable mock). Errors never leak
> personal data.

### Phase 8 — Portfolio (represented brands, contracts and goals)

The **portfolio** records **what the Acme represents** commercially: the **brands/suppliers** it sells
on behalf of third parties (the Acme is a representative/GSA), the **representation contracts** that
grant that right and the **goals per brand**. It does **not** touch prices or commission — it is the
**reference** ("which brand") for quoting, commission and the Intelligence, and it helps governance
track contracts and goals.

What the operator does:

- **Register a brand:** provides the brand **identifier** (e.g. `ALAMO`) and the **display name**. The
  brand starts **active**. Two brands cannot share the same identifier.
- **Deactivate a brand:** when the representation ends, the brand becomes **inactive** (kept for history,
  but no longer actively represented).
- **List/look up brands:** see all, or filter by **active/inactive**.
- **Register a representation contract:** provides the **validity** (from/to), the **contract document**
  (already stored in the document vault — Compliance) and, optionally, **reference conditions** (not
  prices). Selling a brand **without an in-force contract** is **not blocked** — the system only
  **signals** (alerts), and whoever makes the sale decides.
- **Check contract coverage:** ask whether a brand has an **in-force contract** on a date — a support
  lookup (alert), never a block.
- **Expiring-contract alert:** the system signals, **once per contract**, the contracts that are
  **about to expire** (within 30 days) or already expired, so governance can act. It is a **warning**,
  not a block.
- **Set a goal per brand:** pick the brand, the **period** (a year `2026` or a month `2026-06`) and the
  **metric** — **revenue** (an amount in BRL) or **volume** (a count of sales). Each brand has **one**
  goal per period and metric.
- **Attribute a sale to a brand:** record that a **booking** belongs to a represented brand. It is this
  link that lets the system tally the sale under the right brand.
- **Track realized vs goal:** the system shows, for a brand and period, **how much was realized** and
  the **attainment percentage**. The realized comes from the brand's **confirmed sales** (volume) and
  their **realized spread** (revenue) — computed from the sales events, **without changing** the sale.
  Sales with no attributed brand count toward no goal.

> For IT: `POST /api/portfolio/brands`, `GET /brands/{id}`, `GET /brands?status=`,
> `DELETE /brands/{id}` (deactivate); `POST /brands/{brandRef}/contracts`,
> `GET /brands/{brandRef}/contract-coverage?on=`; `POST /contracts/flag-expiring` (triggers the expiry
> alert); `POST /brands/{brandRef}/goals`, `GET /brands/{id}/goals/{period}/progress`,
> `POST /brands/{brandRef}/sales` (sale→brand intake). Identifiers from other contexts (document,
> booking, case) are **values**, no FK; no price/commission lives here — the realized is just a
> read-model projection of the sales events.

### Phase 8 — Internal patrimony (equipment, licenses and other goods)

**Internal patrimony** (*Assets*) records the **Acme's own goods** — equipment, **software licenses**
and other goods — with the acquisition **cost** and the links to the corresponding **document** (in the
document vault / Compliance) and **finance ledger entry**. It is a **lean** registry: it ties
cost↔document together and **warns when a license is about to expire**; it is **not** a full
asset-management system (no depreciation, no maintenance/IT tickets, no resale stock). It is **patrimony,
not a product**: it never enters pricing or a sale.

What the operator does:

- **Register a good:** pick the **type** (equipment, software license or other), enter the
  **identification** (e.g. "JetBrains All Products Pack"), the acquisition **date** and **cost** and,
  optionally, the **supplier**, the **document** (invoice/contract already in the vault) and the **cost
  entry** (in finance) — the latter two are referenced by **identifier**, without duplicating the data.
  The good is born **active**. A **software license** **requires an expiry date**.
- **Look up/list goods:** view a good by its id, or list filtering by **type** and/or **status**
  (active/retired). You can also ask for the **licenses expiring** within the next **N** days.
- **Retire a good:** when a good leaves use, record the **retirement** with a **reason**; the system
  keeps **who** retired it and **when** (audit). Retirement is **final** — a retired good cannot be
  retired again.
- **Expiring-license warning:** the system signals, **once per license**, the licenses that are **about
  to expire** (within 30 days) or have expired, so IT/governance can renew in time. It is a **warning**,
  not a block.

> For IT: `POST /api/assets`, `GET /assets/{id}`,
> `GET /assets?type=&status=&expiringWithinDays=`, `POST /assets/{id}/retire` (retire with a reason),
> `POST /assets/flag-expiring` (triggers the expiry warning). The document (Compliance) and ledger-entry
> (Finance) identifiers are **values**, no FK; no sale price lives here. If the business needs **full
> asset management** (depreciation, maintenance), the recommendation is to **buy** a dedicated system and
> use this module as the registry/integration point.

### Phase 8 — People (HR): collaborators, journey and time-bank

The **People** module is the **minimal HR** capability built **on top of the existing time clock**:
it turns the **operational mirror** of the clock (which the clock robot collects — day-to-day data,
**not** the legal document) into the **period journey**, the **time-bank** and **discrepancy
alerts** for HR to treat. **It is not payroll:** it does not compute eSocial, FGTS, vacation or the
13th salary — for that the path is to **buy/integrate** a payroll system; here live the
**collaborator**, the **journey** and the **balance**.

What the operator does:

- **Register a collaborator:** provide an **identifier** (registration/code, unique), the **admission
  date** and the **contracted daily journey** as `HH:mm` (e.g. `08:00`). The collaborator starts
  **active** (status: active, on leave or terminated). The **employment contract** (a document kept
  in the vault) can be referenced by value, without duplicating the data.
- **Process a period journey:** for a collaborator and a month (`YYYY-MM`), the system builds the
  **journey** from the already-collected operational mirror and computes the **time-bank**:
  **balance = worked hours − contracted hours** for the period. A **positive** balance is **overtime**;
  a **negative** one is **shortfall** (a negative bank is allowed by law). The calculation **measures**
  the balance — it does **not** pay overtime nor grant time off (that is payroll).
- **Read the journey and time-bank:** see the built period journey and the **time-bank** (worked
  hours, contracted hours, signed balance `+`/`−`, and how many discrepancies the period has). E.g.
  worked `176:20`, contracted `176:00` → balance `+00:20`.
- **Treat discrepancies:** when the clock has an **odd punch** (an entry without its exit), a
  **missing punch** or an **incoherent journal**, the system **opens an alert** (discrepancy) in a
  **human-treatment queue** — and **never auto-corrects**. The queue can be filtered by **period** and
  by **status** (open/resolved).
- **Archive the payslip:** the processed payslip/mirror is **stored in the document vault**
  (Compliance) as a **payroll** document, with a **5-year retention** and flagged as **personal data**
  (audited access — LGPD). HR attaches the file; the system handles the retention deadline.

> For the technically minded: `POST /api/people/employees` (register), `GET /employees/{id}`,
> `GET /employees?status=`, `POST /employees/{id}/journey` (process the period journey),
> `GET /employees/{id}/journey?period=`, `GET /employees/{id}/timebank?period=`,
> `GET /api/people/discrepancies?period=&status=` (discrepancy queue),
> `POST /employees/{id}/payslip` (archive the payslip in the vault). The **clock mirror** is always
> treated as **operational, non-legal** data — the legally binding document (the signed AFD/AEJ)
> lives in the vault, coming from the clock's official export (not from this screen). **Heavy
> payroll** (eSocial/FGTS/13th) = **buy/integrate**.

### Phase 8 — Platform (IT): e-CNPJ certificate, jobs and system audit

The **Platform** module is the **operated infrastructure** that underpins the fiscal and integration
modules. It holds **no business rule** — it **guards** secrets, **governs** the automatic routines
(jobs) and **records** the system audit. It is aimed at the **IT team**.

What the IT operator does:

- **Monitor the e-CNPJ certificate:** the digital certificate (ICP-Brasil) is mandatory to issue
  NFS-e and sign the time clock. The system **keeps the certificate encrypted** (the secret material —
  the private key and password — **never** appears on screen, in a log or a report) and shows **only
  the public data**: holder, validity, **days to expiry** and status (valid / expiring / expired).
  When validity approaches (30 days) the system **raises an alert** automatically — so the certificate
  never expires unnoticed and blocks invoice issuance.
- **See the job catalog and history:** the system lists the **automatic routines** (the time-clock
  crawler, the sweeps for SLA deadlines, expiring licenses, expiring contracts, vault retention and
  certificate validity) and the **history of each run**: when it started and finished, whether it
  **succeeded, failed or was skipped** (because it had already run for that window), and how many items
  it handled. A failure shows up **as a failure** — the system **never** masks a failure as success.
- **Trigger a routine manually:** when needed, IT can **run a routine on demand** (e.g. re-run the
  expiring-license sweep). The system guarantees **only one run at a time** (if it is already running,
  it reports it as **running/locked**) and that it **does not duplicate** the window's work.
- **Query the system audit:** an **append-only** record (it cannot be deleted or rewritten) of
  **security, integration and job** facts — who did what, when. It can be filtered by **actor**,
  **type** and **time window**. The audit keeps **metadata only** — **never** the certificate material.

> For the technically minded: `GET /api/platform/certificate/status` (certificate status — **metadata
> only**), `POST /api/platform/certificate` (custody a certificate; the material is encrypted and never
> returned), `GET /api/platform/jobs` (catalog), `GET /api/platform/jobs/runs?job=&status=` (history),
> `POST /api/platform/jobs/{name}/trigger` (manual trigger, replies 202; already running = 409),
> `GET /api/platform/audit?actor=&type=&from=&to=` (audit). **Where** the certificate is actually
> custodied (cloud vault/HSM) and whether it is A1 (file) or A3 (token) is the owner's infra decision;
> today the material is encrypted (AES-256-GCM) with the key held **outside the database**.

### Phase 8 — Signing in (login), roles and access audit

From this version the system has **real login**: each person signs in with a **username and password**,
and **what each one may do depends on their role**. Before, the system used a "development user" with
access to everything — now security is real and **the backend is the authority**: it checks the role on
every action (the screen never decides on its own).

How it works, in practice:

- **Sign in (login).** Open the **"Sign in"** screen, enter your **username** and **password** and
  confirm. If correct, you are in and your name appears at the top with a **"Sign out"** button. If the
  username or password is wrong, a **generic** message appears ("invalid username or password") — on
  purpose, the system **does not say** which one was wrong (security).
- **Sign out.** The **"Sign out"** button at the top ends the session and returns to the login screen.
- **Roles (what each one may do).** Each user has one or more **roles**: **Director**, **Finance**,
  **Operations**, **IT**, **Policy Admin** and **Viewer**. **Sensitive actions** require the right role,
  for example:
  - **issue a commission invoice** and **close the month** → **Finance** role;
  - **trigger a routine (job) / custody the certificate** → **IT** role;
  - **issue a commercial directive** → **Director** role (a policy rule → Director or Policy Admin).
  If you attempt an action without the required role, the system **refuses** ("access denied") and
  **records the attempt** in the audit.
- **Access audit.** Every **login** and every **access denial** is recorded (who, which action, when) —
  **never** storing the password or the token. It is the trail IT/management consults to follow access.

> **Updated in Phase 13:** the **in-house** authentication described above (username/password inside the
> system) was **replaced** by **corporate single sign-on (SSO) via an external identity provider** — see
> the *"Phase 13 — Sign in with SSO"* section below. Roles, permissions and the access audit **stay the
> same**; only **the way you sign in changed**.

### Phase 8 — Administrative suppliers and contracts (utilities, software)

This version delivers the **administrative desk**: a **simple** registry of the company's
**administrative suppliers** (power, water, telephone, software/service subscriptions) and the
**contracts** that sustain them — so that each **expense** lands in the right **Accounts Payable**
entry and **points at the document** the monthly close requires (the bill, the self-employed's RPA,
the PJ service's NFS-e). **Not** to be confused with the **tourism brands/suppliers** (those live in
"Portfolio").

How it works in practice:

- **Register an administrative supplier.** Provide the **type** (Utility, Software, Service or Other),
  the **name** and, where applicable, the **CNPJ/CPF**. The supplier is born **active**.
- **Register a contract.** For a supplier, record the **validity** (start and, if any, end), the
  **recurrence** (e.g. monthly), the **amount** and the **contract document** (already kept in the
  document vault). An inconsistent validity window (end before start) is rejected.
- **Record a month's expense.** Provide the **supplier**, the **month** (e.g. 2026-06), the **amount**
  and the expense **kind**. The system **automatically creates** the **Accounts Payable** entry and
  **tells you which documents** you must attach for the month to close:
  - **utility** (power/water/telephone) → requires the **bill** (and the proof of payment when paying);
  - **self-employed service** (individual) → requires the **RPA**;
  - **company (PJ) software/service** → requires the **NFS-e**;
  - **other** → no mandatory document at registration.
  Recording **the same expense twice** (same supplier, month and kind) is **rejected** (no double
  posting).
- **The golden rule applies here too.** While the **bill/document** is not attached, that entry
  **blocks the month from closing** — exactly like any other bill. Admin **does not** close the month
  nor waive a document: it only **generates the entry** and **points at the document**; Finance+
  Compliance are what hold the close.
- **Contract-expiry alert.** The system **warns** when an administrative contract is approaching expiry
  (up to 30 days before) — just an **alert** so you can renegotiate/renew; it **never** blocks anything.
- **Who can act.** Because an administrative expense becomes a financial obligation, **registering a
  supplier/contract and recording an expense** require the **Finance** role; without it, the system
  **denies** and **records** the attempt. Every change is **audited** (the CNPJ/CPF never appears in
  full in that trail — it is personal data).

> For the technically minded: `POST /api/admin/suppliers` (register supplier),
> `GET /api/admin/suppliers?type=&status=` (list), `POST /api/admin/suppliers/{id}/contracts` (register
> contract), `POST /api/admin/expenses` (record expense → returns the entry id and the required
> documents), `POST /api/admin/contracts/flag-expiring` (sweep expiring contracts). Writes require the
> **Finance** role. Full procurement (quotation/purchase order) is **not** part of this module — buy a
> procurement system if required.

### Phase 10 — A new experience (professional screens, shortcuts, theme and dashboard)

This phase **does not change any business rule**: it renews the **entire look and navigation**, giving
the system a professional ERP feel. What you now see and use:

- **Renewed login screen.** The same Phase-8 login, now with a clean look and a **show/hide** toggle on
  the password field. After signing in you land on the **Dashboard**. If your session has expired and
  you try to open a screen directly, the system sends you to login and, once you sign in, **takes you
  back to the screen you wanted**.
- **A session that stays.** When you reload the page, the system keeps you signed in while the access is
  still valid, and otherwise asks you to sign in again. (Since Phase 13 this is **corporate single
  sign-on with real silent token renewal** — see *"Phase 13 — Sign in with SSO"*.)
- **SaaS layout (sidebar + top bar).** On the **left**, the navigation menu (Dashboard, Accounts,
  Exchange, Quotes, Bookings, Reconciliation, Health) highlighting the current screen. On the **top**,
  the command search, the **theme button** and your name with **"Sign out"**. On small screens (mobile)
  the menu becomes a **drawer** opened by the menu button.
- **Light/dark theme.** The **sun/moon** button on top switches between **light** and **dark**. Your
  choice is **saved** in the browser; the first time, the system follows your computer's preference.
- **Command palette (`Ctrl/Cmd + K`).** Press **Ctrl+K** (or **⌘+K** on Mac) **from any screen** to
  open a search box: type a screen or action name (e.g. "Bookings", "Theme", "Sign out"), use **↑/↓**
  to choose and **Enter** to run it. **Esc** closes it.
- **Keyboard shortcuts.** Outside text fields, press **`g`** then the initial of a screen (e.g. **`g`**
  then **`a`** → Accounts) to navigate quickly. Press **`?`** to open the **shortcut help**.
  (Single-letter shortcuts are ignored while you type in a form, so they never get in the way.)
- **Unsaved-changes warning.** If you start filling a form (e.g. a new account, pinning a rate, a quote
  override) and try to **leave without saving**, the system **asks first** whether you really want to
  leave — preventing the loss of what you typed.
- **Clear states on every screen.** Each screen consistently shows when it is **loading**, when there is
  **no data** ("nothing to show"), when an **error** happened (with a **"Retry"** button) and when you
  **lack permission** to see something (a permission message, instead of a technical error).
- **Dashboard with KPIs.** The landing screen is now a **Dashboard** with summary cards: **Accounts**
  (total and active), **Bookings** (total, pending and confirmed), **Reconciliation** (cases, open,
  in discrepancy and the summed expected spread) and **Exchange** (the prevailing frozen rate).
  Clicking a card **takes you to** the related screen. Each card loads independently and shows its own
  state (loading/error/permission).

> For the technically minded: the screens now use **PrimeNG 21 (Aura theme)** + **Tailwind v4** on top
> of **Angular 22**, keeping all text in the translation mechanism (pt-BR/en). The Dashboard KPIs are
> computed **in the browser** from the list endpoints that already existed — **no new server endpoint**
> was added.

### Phase 11 — Monitoring and version (for operations / IT)

This phase **changes no business rule and no regular-operator screen**: it gives the **IT** team ways to
**watch the system's health and usage** and to **know which version is running**. It is the ERP's
observability foundation (metrics, logs and monitoring dashboards).

- **Which version is running.** The **`/api/version`** endpoint (open, no login) returns the system
  **version** (e.g. `0.22.0`), the **commit id** and the **build date/time** — a one-glance way to see
  exactly what is deployed (useful for support and a footer/"about").
- **System health (probes).** **`/actuator/health`** (and its *liveness*/*readiness* sub-items) stay
  **open** so infra tooling can check the system is alive and ready. The "Health" screen you already
  knew keeps working.
- **Metrics (IT only).** **`/actuator/prometheus`** publishes the system **metrics** (app memory/CPU,
  request volume and timing, and **business counters** such as confirmed bookings, composed quotes,
  issued commission invoices, logins). This endpoint is **restricted to the IT role** — without the
  role you get **access denied**; unauthenticated, you do not get in.
- **Grafana dashboards.** Alongside the system, a **monitoring stack** (Prometheus + Loki + Grafana)
  comes up via `docker compose`. In **Grafana** (port 3000), IT sees the **"Acme Travel ERP — Backend
  Overview"** dashboard (memory, request rate, CPU and business events) plus the centralized **logs**.
  In the container, the system logs are **structured (JSON)** with each request's **correlation id** —
  and **never** carry a password, token or personal data.

> For IT: so Prometheus can scrape the protected metrics endpoint, generate a **token** for a user with
> the **IT** role and point it at `infra/prometheus/scrape-token` (see `infra/prometheus/README.md`).
> The monitoring stack is **config/infra** — it is not part of the backend build/tests.

### Phase 13 — Sign in with SSO (corporate single sign-on)

This release swaps the login for **SSO (corporate single sign-on)**: instead of typing a username and
password **inside** the ERP, you sign in with your **corporate account**, on the **identity provider's
page** (the system uses **Keycloak** in development). It is the same "sign in with your company account"
pattern you already see in many systems. **Roles, permissions and the access audit did not change** —
only **the way you sign in changed** (and it is safer: the ERP **no longer stores your password**).

How it works in practice:

- **Sign in.** Open the **"Sign in"** screen and click **"Sign in with SSO"**. You are taken to the
  **provider's login page** (the company account); enter your **username and password there**. On
  success you come back to the system **already authenticated**, on the **Dashboard**, with your name and
  **"Sign out"** at the top. If the password is wrong, the **provider itself** shows a generic error and
  you **do not get in**.
- **A session that stays valid (silent renewal).** While you use the system, it **renews your access on
  its own**, in the background — no re-login every few minutes. When the session truly ends, you are
  taken back to the login. (Before, the session was only **revalidated**; now it is **truly renewed**.)
- **Sign out.** The **"Sign out"** button ends the session **in the system and at the provider** (single
  sign-on) and returns to the sign-in screen.
- **Roles and permissions (unchanged).** Still in force: **Director, Finance, Operations, IT, Policy
  Admin and Viewer**; sensitive actions require the right role (issue NF/close the month → Finance;
  trigger job/custody certificate → IT; directive → Director). Without the role, **access denied** and
  the attempt **stays in the audit**. The **backend is still the authority** — the screen only mirrors.

> For the technically minded: the backend became an **OAuth2 Resource Server** that validates the
> **external provider's token (OIDC)** via **JWKS** (with key rotation). The in-house login endpoint
> (`POST /api/identity/login`) was **removed** — login now happens at the provider.
> `GET /api/identity/me`, `GET /api/identity/roles` and `GET /api/identity/access-audit` remain. The
> identity provider **in production** is the owner's decision (the shipped Keycloak serves dev/E2E, with
> a ready realm: roles, the web app and sample users — **development only**). It comes up with
> `docker compose up`.

### Phase 16a — Operator screens: Finance, Billing, Payouts and Compliance

This release **opens four new screens** to operate areas that previously existed only "under the hood"
(the logic already ran, but there was **no screen**). No new rules: these are **screens over what the
system already did**. The first three appear in the menu **only for users with the Finance role**;
**Compliance** appears for any authenticated user. On every screen, an empty list shows a clear
"nothing to show" notice, and a missing permission for an action shows **"access denied"** (the server
is the authority — the screen only mirrors it).

- **Finance (AP/AR ledger and monthly close).** Menu **"Finance"**. Shows **payable and receivable
  entries**, filtered by **direction** (payable/receivable), **status** (provisional/confirmed/settled)
  and **period** (YYYY-MM). You can **create an entry** (direction, party, amount in its original
  currency, type and period). Under **"Period & close"**, look up a month to see the **trial balance per
  currency** (payable, receivable and net — **currencies are never mixed**) and the period status, and
  **close the month** with one click. The **"golden rule"** applies: if an entry is **missing its
  mandatory document**, the close is **refused** and the reason is shown on screen.
- **Billing (commission invoice / ISS).** Menu **"Billing"**. **Create a draft** commission invoice
  (from the commission entry, giving the **base = the commission**, never the full package, and the
  municipality). **Look up an invoice by id** to see base, **ISS**, withholdings, status, number and
  verification code. From the screen you **issue** it (computes ISS, signs, transmits and archives the
  document — requires the Finance role) and **cancel** an issued one with a reason.
- **Payouts & settlements.** Menu **"Payouts"**. Lists **agent commission repasses**, **supplier
  settlements** (with a **rate** when in foreign currency, showing the BRL settled amount) and
  **customer refunds** (which reference their **origin**), filtered by kind, status and payee. **Open a
  payout** to see its **installments**; **create** a new one and **execute** the payment. If the provider
  **fails**, the status becomes **"failed"** explicitly — **never** a false "paid".
- **Compliance (document vault, requirements and retention).** Menu **"Compliance"**. Run the
  **close-check** for a month: the system says whether the period **may close** or lists the **pending
  entries** (which entry and **which document is missing**). **Upload a document** to the vault (type,
  issue date, signed format when applicable, and whether it carries **personal data**) and **look up a
  document by id** to see **type, hash, issue date and the retention deadline** computed by the system
  (5 years for fiscal/payroll/time-clock; 10 years for contracts). The **internal content/file is never
  exposed** — only the metadata.

> For the technically minded: a **frontend-only** slice over APIs that already existed (`/api/finance`,
> `/api/billing`, `/api/payouts`, `/api/compliance`) — **no new endpoint**, no contract or database
> change (SPEC-0029 / DL-0109). It is the **first of the four Phase 16 slices** paying off the deferred-
> screen debt; the next ones bring the remaining areas (commercial cycle, intelligence, back-office).

### Phase 16b — Operator screens: After-sales, Sourcing, FX desk and Cancellation

This version opens **four more screens**, now for the **commercial cycle**. As in 16a, they are
**screens over what the system already did** (no new rules). They appear in the menu **for the
Operations role** (menu tidiness only — the **server stays the authority**: if you lack permission for
an action, the screen says **"access denied"**). Everywhere, an empty list shows a clear "nothing to
show" notice.

- **After-sales (cases and SLA).** Menu **"After-sales"**. Lists **cases** (complaint, change request,
  cancellation, refund, info) filtered by **type**, **status** and **booking**, plus a filter to show
  only those that **breached their SLA**. **Open a case** pointing at a booking and a type; then **drive
  the handling** with the buttons — **assign**, **wait** and **close** — and **resolve** it choosing the
  outcome: **approve refund** (triggers a refund payout), **approve cancellation** (triggers the booking
  cancellation with the policy penalties), **resolve with no action** or **reject**. The **SLA-breached**
  flag is only an alert — it **never blocks** the flow. The screen also shows the accumulated **cost to
  serve** and the linked payout when there is one.
- **Offer sourcing.** Menu **"Sourcing"**. **Record where an offer came from**: product description,
  base price, **origin** (own integrated portal, external site, third-party catalog or raw demand) and
  the **integration level** (none, inbound only, or two-way). Then **look up an offer by id** to review
  those data. It makes clear, on each sale, whether the source is integrated or typed in by hand.
- **FX desk (exposure and positions).** Menu **"FX desk"** — the **companion** to the **pinned-rate**
  screen (which stays the same). It shows the **book exposure**: the **accrued subsidy** (how much the
  house intentionally "eats" on the FX) plus the **market drift** (the risk that moves with the rate),
  with an **alert** when the drift crosses the threshold. You can **record the market rate** (manual
  contingency) and see its **history**, **look up a booking's position** (pinned rate, market at freeze,
  subsidy and drift) and view the **PromoFx report for a month** (subsidy × drift × total gap).
- **Cancellation policy.** Menu **"Cancellation"**. **Look up and configure** the policy of a
  product/supplier: the **type** (standard, **all sales final** or custom), whether it is **refundable to
  the supplier**, **who bears** the penalty (agency, Acme or supplier), whether we are the **merchant of
  record**, the **no-show fee** and the **penalty windows** (hours-before × percentage). The screen makes
  the **"merchant trap"** explicit: in "all sales final", the supplier cost is **still due** even when the
  customer is refunded.

> For the technically minded: a **frontend-only** slice over APIs that already existed
> (`/api/aftersales`, `/api/sourcing`, `/api/exchange` — exposure/positions/market-rate/PromoFx — and
> `/api/products/*/cancellation-policy`) — **no new endpoint**, no contract or database change
> (SPEC-0029 / DL-0109). It is the **second of the four Phase 16 slices**; intelligence/commercial
> policy (16c) and back-office/HR/IT (16d) remain.

## 4. Glossary

- **Backend / server:** the part of the system that processes the rules and talks to the database.
- **Database:** where the information is stored (PostgreSQL).
- **Health:** a quick check that the system is up and responding.
- **Provenance / Sourcing:** the record of **where** an offer came from (own portal, external
  website, catalog, one-off request) and how integrated it is.
- **Integrated quote:** a quote created from a **closed, trusted price** coming from an external
  system; the ERP does not recompute the price (no suggestion, no manual adjustment).
- **Webhook:** a message an external system sends automatically to the ERP (here, the quote from the
  quotation site), always **signed** to ensure it is legitimate.
- **Cancellation policy:** a product's cancellation rules — type, penalty windows, who pays,
  refundable, and whether the sale is merchant or affiliate.
- **Penalty window:** "up to X hours before the service, charge Y% penalty". Cancelling earlier than
  all windows yields no penalty.
- **Final sale (`ALL_SALES_FINAL`):** a sale non-refundable from the supplier's standpoint: the cost
  with the supplier remains due even if the customer is refunded.
- **Merchant of record:** when Acme/the portal **takes on** the charge and the refund for a brand;
  the opposite is **affiliate** (the supplier takes it on).
- **Merchant trap:** on a final merchant sale, the supplier cost **and** the customer refund are
  **two obligations that do not cancel each other out** — the system keeps both visible so money is
  not lost invisibly.
- **No-show:** the customer does not show up; it generates a penalty, waivable with proof of a
  cancelled flight when the policy allows.
- **Entry (AP/AR):** the record of a **payable** (AP) or **receivable** (AR), with amount, currency,
  party, type and the month it belongs to.
- **Period / accounting month (`YYYY-MM`):** the month the entries belong to; it is the unit of the
  monthly close.
- **Monthly close:** locks the period; it only closes if every entry has the required document
  (checked by the document vault / Compliance).
- **Provisional entry:** an entry already recorded but that may still lack the required document; it
  becomes **confirmed** when validated.
- **Consent (LGPD):** the subject's authorization to receive a communication (e.g. newsletter);
  without it, nothing is sent. Recorded with purpose, legal basis and date; a **revocation** is
  appended as a new decision and the history is preserved.
- **Segment:** an audience defined by **criteria over already-existing data** (e.g. account type,
  region); it collects no new data.
- **Campaign:** a dispatch to a segment, with its own **code** used to measure attribution.
- **Suppression (on dispatch):** when a recipient is **excluded** from a send for lack of consent; the
  system **counts** the suppressed ones instead of failing the whole dispatch.
- **Attribution / conversion:** the link between a campaign **code** and a **booking**; when the
  booking is confirmed it becomes a **conversion** (the campaign-return signal for Intelligence).
- **LGPD erasure:** honouring the subject's request to delete their **marketing data**, while keeping
  the **revocation proof** (so they are not re-included) and whatever **another law** requires to keep.
- **Internal patrimony (*Assets*):** the company's own goods (equipment, software licenses, other
- **Time-bank:** the balance of a period = worked hours − contracted hours; positive is overtime, negative is shortfall (a negative balance is allowed by law). Here the system **measures** the balance; paying overtime or granting time off is payroll work.
- **Journey (of the period):** the hours the collaborator actually fulfilled in the month, built from the clock operational mirror.
- **Clock discrepancy:** an alert for an odd/missing punch or an incoherent journal, opened for HR to treat — the system never auto-corrects.
- **Payslip:** the pay statement; here it is stored in the vault (payroll) with a 5-year retention and treated as personal data.
  goods), with cost, document and a lifecycle (active/retired). It is a registry, not a product.
- **Retiring a good (*retire*):** marking a good as out of use, with a reason and an audit (who/when).
  It is final.
- **Expiring license:** a software license whose **expiry date** is near (within 30 days) or has passed;
  the system **warns** so it can be renewed, without blocking anything.
- **e-CNPJ certificate:** the company's digital certificate (ICP-Brasil), mandatory to issue invoices
  and sign the time clock. The system **keeps it encrypted** and shows **only the public data**
  (validity, holder) — never the key/password.
- **Secret custody:** keeping a secret material (certificate, password) **encrypted**, reachable only
  by the system, never exposed. The encryption key lives **outside the database**.
- **Automatic routine (*job*):** a task the system runs by itself on a schedule (the clock crawler, the
  deadline sweeps). **Job governance** guarantees each runs **one at a time**, **without duplicating**
  the window, with a **history** of every run.
- **System audit:** the **append-only** record of security/integration/job facts (who, what, when) for
  traceability. It keeps **metadata only**, never a secret.
- **Metric:** a number the system continuously publishes about itself (memory, response time, how many
  bookings confirmed, etc.) so IT can watch its health and usage.
- **Prometheus / Grafana / Loki:** the monitoring tools — **Prometheus** collects the metrics, **Loki**
  aggregates the logs and **Grafana** shows it all on dashboards. They come up together with the system.
- **Structured (JSON) log:** the system's event record in machine format (JSON), with each request's
  correlation id and **without** password/token/personal data.
- **Version endpoint (`/api/version`):** the (open) endpoint that reports which version/commit/build
  date is running.

## 5. Manual version history

| Version | Phase | What changed in the manual |
|---|---|---|
| 0.1.0 | 0 — Foundation | First version: overview, how to access and the "System health" screen. |
| 0.4.0 | 3 — Integration | Offer provenance (*Sourcing*): manual offer recording and the automatic quote from the quotation site (INTEGRATED branch). |
| 0.5.0 | 4 — Cancellation | Per-product cancellation policy, penalty windows, no-show with proof-of-cancelled-flight waiver, and the "merchant trap" (two obligations that do not net out on a final sale). |
| 0.10.0 | 8 — Finance (full) | Accounts Payable/Receivable and the monthly close with the "golden rule" (no close without the invoice); **automatic entries** from booking cancellations and no-shows (exactly once, no duplication); per-currency trial balance for the period. |
| 0.13.0 | 8 — AfterSales | After-sales: support cases (complaint/change/cancellation/refund/info) tied to a booking; **governed SLA deadlines** (24h/72h/48h, tightenable by directive) with a non-blocking breach alert; resolution that **forwards** a refund to Payout (once, without cancelling the supplier obligation) and a cancellation to the booking; per-case "cost to serve". |
| 0.14.0 | 8 — Marketing | B2B marketing with mandatory **LGPD consent**: record/revoke/look up consent (history preserved); **segment** over existing data with a reach **preview**; **campaign** that **sends only to those who consented** (suppressed are counted, no double-send) via a newsletter provider; **attribution** code→booking that becomes a **conversion signal** for the DSS; **LGPD erasure** that deletes marketing data but preserves the revocation proof. |
| 0.15.0 | 8 — Portfolio | Representation: register/deactivate/list **represented brands** (unique identifier); register **representation contracts** (validity + a vault document), with an **alert** (not a block) for selling without an in-force contract and an **expiring-contract warning** (within 30 days); set **goals per brand** (volume or revenue) and track **realized vs goal** from the brand's **confirmed sales**. It touches no price or commission. |
| 0.16.0 | 8 — Patrimony (Assets) | Registry of **internal patrimony** (equipment, software licenses, other goods): register with **type/identification/date/cost** and value links to the **document** (vault) and the **finance entry**; a software license requires an **expiry date**; an audited, **final retirement** (with a reason); list/filter by type/status and by **expiring licenses**; a **warning** (once per license) for licenses expiring within 30 days. It is patrimony, not a product — no price/sale; full asset management = buy. |
| 0.17.0 | 8 — People | Minimal HR on top of the time clock: register a **collaborator** (unique identifier, admission, **contracted journey** HH:mm, status active/on-leave/terminated); **process the period journey** from the operational mirror and compute the **time-bank** (balance = worked − contracted; overtime/shortfall, negative allowed) — it only **measures**, it is not payroll; **read** the journey and time-bank; **discrepancies** (odd/missing punch, incoherent journal) become an **alert** in a treatment queue, **no auto-correction**; **archive the payslip** in the vault (payroll, 5-year retention, personal data). Heavy payroll (eSocial/FGTS/13th) = buy/integrate. |
| 0.18.0 | 8 — Platform | IT infrastructure: **e-CNPJ certificate custody** with the material **encrypted** (the key/password never appears) — the screen shows **only metadata** (holder, validity, days-to-expiry, status) and the system **alerts** when the certificate is about to expire (30 days); **job governance** — catalog and **history** of the automatic routines, **manual trigger** (only one run at a time = 409 if already running; no duplication within the window), and a failure shows up **as a failure** (never masked as success); **append-only system audit** of security/integration/job events (who/what/when), filterable, **metadata only** (never the secret). |
| 0.19.0 | 8 — Identity | **Real login**: sign in with **username and password** ("Sign in" screen), name and "Sign out" at the top; **generic** error that never reveals whether the user exists. **Roles and permissions** (Director/Finance/Operations/IT/Policy Admin/Viewer): **sensitive actions require the role** (issue NF and close the month → Finance; trigger job/custody certificate → IT; directive → Director) — without the role, **access denied**, recorded. **Access audit** (logins and denials; who/action/when, **no password/token**). The **backend is the authority** — the screen only mirrors. Corporate single sign-on (external provider) = next step (Phase 13). |
| 0.20.0 | 8 — Admin (administrative suppliers/contracts) | **Administrative desk**: a **lean** registry of **administrative suppliers** (power, water, telephone, software/service, self-employed) and their **contracts** (validity, recurrence, amount, document). **Recording a month's expense** automatically creates the **Accounts Payable** entry with the right kind and **points at the required documents** (utility → bill; self-employed → RPA; PJ service → NFS-e); **idempotent** (no duplicates). **The golden rule applies here**: an expense **without its document blocks the month from closing**. **Contract-expiry alert** (up to 30 days, alert only). **Registering/recording requires the Finance role**; every change is **audited** (CNPJ/CPF never shown in full). Full procurement (quotation/order) = buy if required. **End of the 8x block.** |
| 0.21.0 | 10 — UX & professional frontend | **New experience** (no rule changes): **SaaS layout** (sidebar + top bar + mobile drawer); **light/dark theme** with the choice saved; **command palette `Ctrl/Cmd+K`** + shortcuts (`g`+key, `?` help); renewed **login** with **silent session revalidation** (returns to the intended screen); **unsaved-changes warning** when leaving a form; **real states** (loading/empty/error/permission) on every screen; a **Dashboard** with Accounts/Bookings/Reconciliation/Exchange KPIs computed in the browser. Screens built with **PrimeNG 21 + Tailwind v4** on Angular 22; **no new server endpoint**. Graduates DL-0003. |
| 0.22.0 | 11 — Observability & monitoring | **Monitoring and version (for operations/IT)**, with no business-rule change: **`/api/version`** (open) returns version/commit/build date; **health probes** (`/actuator/health`) stay open; **metrics** (`/actuator/prometheus`) — technical (memory/CPU/requests) and **business** (bookings/quotes/invoices/logins) — **restricted to the IT role**; a **monitoring stack** (Prometheus + Loki + Grafana) that comes up via `docker compose`, with the "Acme Travel ERP — Backend Overview" dashboard and centralized logs; **JSON logs** with the correlation id and **no** secret/personal data. |
| 0.23.0 | 13 — Professional Identity/AuthZ (graduates SPEC-0024) | **Corporate single sign-on (SSO)**: signing in now goes through the **company account** on the **identity provider's** page (Keycloak in dev), with the **"Sign in with SSO"** button and **real silent session renewal**; the ERP **no longer stores passwords**. **Roles, permissions and the access audit stay the same** — only the way you sign in changed. **Breaking change:** the old in-house login (`POST /api/identity/login`) was **removed** (login is now at the provider). |
| 0.24.0 | 16a — Operator screens: Finance & Compliance | **Four new screens** over APIs that already existed (no new rules): **Finance** (AP/AR ledger with filters, per-currency trial balance and the monthly close with the "golden rule"), **Billing** (draft/issue/cancel of the commission invoice, with ISS and withholdings), **Payouts** (agent repass, supplier settlement with rate, customer refund, with installments and execution — a failure shows as a failure), **Compliance** (close-check with pending entries, vault upload and document lookup by id with hash and retention deadline). Finance/Billing/Payouts appear in the menu **only for the Finance role**; Compliance for any authenticated user. **First of the four Phase 16 slices** (pays off the deferred-screen debt — DL-0109). |
| 0.25.0 | 16b — Operator screens: commercial cycle | **Four new screens** over APIs that already existed (no new rules): **After-sales** (cases with filters and SLA, an assign/wait/close state machine and a resolution that triggers refund/cancellation; a breached SLA only alerts, never blocks), **Sourcing** (register/look up an offer's provenance and integration level), **FX desk** (companion to the pinned rate: book exposure with subsidy+drift and an alert, market rate and history, position by booking and the PromoFx report), **Cancellation** (look up/configure the per-product policy: type, windows, cost bearer, no-show and the "merchant trap"). They appear in the menu **for the Operations role** (the server remains the authority). **Second of the four Phase 16 slices** (DL-0109). |

> Note: the manual focuses on the slices with a user screen/journey; internal capabilities of Phases
> 1, 2 and 5–8a appear here as they gain direct operator use. This English manual is the mirror of
> `docs/MANUAL.md` (pt-BR) — keep both in sync on every slice.
>
> Phase 15 — Bilingual docs (chore, no version bump): bilingual coverage, previously the manual only,
> now also includes the **README** (`README.en-US.md`) and the **consolidated en-US changelog**
> (`docs/release-notes/CHANGELOG.en-US.md`). Technical reports stay pt-BR only (Rule Zero).
