# Instruction Manual — Acme Travel ERP

> Manual in **English**, for the end user/operator (non-technical). Describes **what the system
> already does today**. Updated **on every delivered slice** (see the *User manual* command in
> `CLAUDE.md`). Portuguese version: `docs/MANUAL.md` (kept in sync).
>
> **System version:** 0.10.0 · **Current phase:** 8 (Finance, full)

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

## 5. Manual version history

| Version | Phase | What changed in the manual |
|---|---|---|
| 0.1.0 | 0 — Foundation | First version: overview, how to access and the "System health" screen. |
| 0.4.0 | 3 — Integration | Offer provenance (*Sourcing*): manual offer recording and the automatic quote from the quotation site (INTEGRATED branch). |
| 0.5.0 | 4 — Cancellation | Per-product cancellation policy, penalty windows, no-show with proof-of-cancelled-flight waiver, and the "merchant trap" (two obligations that do not net out on a final sale). |
| 0.10.0 | 8 — Finance (full) | Accounts Payable/Receivable and the monthly close with the "golden rule" (no close without the invoice); **automatic entries** from booking cancellations and no-shows (exactly once, no duplication); per-currency trial balance for the period. |
| 0.13.0 | 8 — AfterSales | After-sales: support cases (complaint/change/cancellation/refund/info) tied to a booking; **governed SLA deadlines** (24h/72h/48h, tightenable by directive) with a non-blocking breach alert; resolution that **forwards** a refund to Payout (once, without cancelling the supplier obligation) and a cancellation to the booking; per-case "cost to serve". |
| 0.14.0 | 8 — Marketing | B2B marketing with mandatory **LGPD consent**: record/revoke/look up consent (history preserved); **segment** over existing data with a reach **preview**; **campaign** that **sends only to those who consented** (suppressed are counted, no double-send) via a newsletter provider; **attribution** code→booking that becomes a **conversion signal** for the DSS; **LGPD erasure** that deletes marketing data but preserves the revocation proof. |

> Note: the manual focuses on the slices with a user screen/journey; internal capabilities of Phases
> 1, 2 and 5–8a appear here as they gain direct operator use. This English manual is the mirror of
> `docs/MANUAL.md` (pt-BR) — keep both in sync on every slice.
>
> Phase 15 — Bilingual docs (chore, no version bump): bilingual coverage, previously the manual only,
> now also includes the **README** (`README.en-US.md`) and the **consolidated en-US changelog**
> (`docs/release-notes/CHANGELOG.en-US.md`). Technical reports stay pt-BR only (Rule Zero).
