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

## 5. Manual version history

| Version | Phase | What changed in the manual |
|---|---|---|
| 0.1.0 | 0 — Foundation | First version: overview, how to access and the "System health" screen. |
| 0.4.0 | 3 — Integration | Offer provenance (*Sourcing*): manual offer recording and the automatic quote from the quotation site (INTEGRATED branch). |
| 0.5.0 | 4 — Cancellation | Per-product cancellation policy, penalty windows, no-show with proof-of-cancelled-flight waiver, and the "merchant trap" (two obligations that do not net out on a final sale). |
| 0.10.0 | 8 — Finance (full) | Accounts Payable/Receivable and the monthly close with the "golden rule" (no close without the invoice); **automatic entries** from booking cancellations and no-shows (exactly once, no duplication); per-currency trial balance for the period. |

> Note: the manual focuses on the slices with a user screen/journey; internal capabilities of Phases
> 1, 2 and 5–8a appear here as they gain direct operator use. This English manual is the mirror of
> `docs/MANUAL.md` (pt-BR) — keep both in sync on every slice.
>
> Phase 15 — Bilingual docs (chore, no version bump): bilingual coverage, previously the manual only,
> now also includes the **README** (`README.en-US.md`) and the **consolidated en-US changelog**
> (`docs/release-notes/CHANGELOG.en-US.md`). Technical reports stay pt-BR only (Rule Zero).
