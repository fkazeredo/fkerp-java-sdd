# ERP Acme Travel — Java + Spring Boot + Angular

> 🌐 **Language / Idioma:** **English** · [Português (pt-BR)](README.md)

A **complete, self-contained** package of the specifications and architecture rules for Acme Travel's
ERP (a GSA — a commercial representative for travel brands). **Stack: Java 21 + Spring Boot on the
backend, Angular on the front, Postgres, modular monolith.** Prose in pt-BR by project convention;
code identifiers in English. This file mirrors [README.md](README.md) (pt-BR) — keep both in sync.

> This package is **Java only**. (A mirror version in Go + React exists, with the same domain and the
> same rules — but it is **not** here, to avoid confusion. This is Java/Spring/Angular, full stop.)

---

## 1. Where to start (read in this order)

1. **`erp-turismo-b2b-redesenho.md`** — the domain document: what Acme is, how it makes money
   (two-sided commission + spread), the 22 contexts and the business rules. **It is the source of truth.**
2. **`CLAUDE.md`** — the operating rules Claude Code follows on **every** task (Rule Zero, authority
   order, "never invent a business rule", Definition of Done, the routing map).
3. **`architecture/`** — the detailed rules, loaded on demand (backend, persistence, modules,
   security, messaging, testing, Angular front-end, etc.).
4. **`docs/ROADMAP.md`** — in what order to build (vertical slices per phase) + the index of the 25 specs
   + **the recommendations for the Open Questions** (starting suggestions you decide on).
5. **`docs/TUTORIAL.md`** — the **7-step loop** you repeat for every slice with Claude Code
   (questions → plan → red test → skeleton → green → refactor → gates/DoD), with real prompts.
6. **`docs/specs/`** — the 25 specifications (0001–0025), one per context. **`docs/adr/`** — the
   architecture decisions (0010–0014) + template.

---

## 2. Package structure

```txt
acme-travel-erp-java/
  README.md                          <- pt-BR front door
  README.en-US.md                    <- this file (en-US mirror)
  erp-turismo-b2b-redesenho.md       <- domain (source of truth)
  CLAUDE.md                          <- operating rules (always loaded)
  architecture/                      <- 12 rule docs (loaded on demand)
    core-principles.md  backend.md  modules-and-apis.md  persistence.md
    messaging-and-integrations.md  security.md  observability.md  delivery.md
    frontend-angular.md  workflow.md  testing.md  simulation-and-mocking.md
  docs/
    ROADMAP.md                       <- slice order + index + Open Question recommendations
    TUTORIAL.md                      <- the per-slice loop (with prompts)
    adr/                             <- 0000-template + 0010..0014
    specs/                           <- 0000-template + 0001..0025 (all 25)
```

---

## 3. How to use it with Claude Code (summary)

The method is **vertical slice, test first**: each slice cuts through migration → domain → API → screen
and is demonstrable at the end. You do **not** create 22 empty modules at once; code is born **one slice
at a time**, in the ROADMAP order.

1. **Setup (once):** run **SPEC-0001** (the skeleton that boots, connects to Postgres, has
   `/api/system/health`, a minimal Angular screen, ArchUnit + Spring Modulith green and CI). Have
   **JDK + Docker** (integration tests use Testcontainers) and always use the project's **`./mvnw`**.
2. **Per slice (SPEC-0002 onward):** follow the loop in `TUTORIAL.md`. Before coding, **decide the Open
   Questions** that affect the slice (the ROADMAP carries starting recommendations) and record the
   decision in the spec.
3. **Non-negotiable gates:** `./mvnw verify` must stay green — it includes ArchUnit and Spring Modulith.
   If they complain, **fix the code, never loosen the rule**.

---

## 4. What to decide before coding (Open Questions)

No business rule was invented: where the redesign does not decide, the spec **records the question**. The
owner decisions that **block** slices are in the `ROADMAP.md` table (Q1–Q8 + the Quoting **price
formula**), now **with a starting recommendation for each**. Take that table to the director/accountant,
mark OK/change, and only then open the owning slice.

Sequencing worth remembering: **Finance (0015) co-delivers with Compliance (0008) in Phase 2** — the
Compliance close-veto depends on the period concept, which belongs to Finance.

---

## 5. Inherited conventions

All specs inherit the **"Project conventions"** block from **SPEC-0001** (Money = `BigDecimal` scale 2;
exchange rate scale 6; HALF_UP; UTC/ISO-8601; `DomainException{code}` with `code` == i18n key; a
`@Version` column; pessimistic locking on financial transitions; no cross-context FK — ids as values;
ports + ACL for the external world; jobs with idempotency/locking/history). ADRs 0010–0013 fix: a
centralized `infra` layer with per-module ports; domain exceptions free of transport concerns; three
hexagonal layers; the Lombok policy. ADR 0014 fixes the initial modules and the slice order.

---

## 6. Release notes & user manual

- **Release notes:** [`docs/release-notes/`](docs/release-notes/) — one pt-BR file per version, plus the
  consolidated [`CHANGELOG.en-US.md`](docs/release-notes/CHANGELOG.en-US.md) (en-US).
- **User manual (end users/operators):** [`docs/MANUAL.md`](docs/MANUAL.md) (pt-BR) ·
  [`docs/MANUAL.en-US.md`](docs/MANUAL.en-US.md) (en-US).

> User-facing docs are bilingual and kept in sync per slice. Technical artifacts (specs, ADRs,
> decision-log, phase/test reports, the build TUTORIAL) stay **pt-BR only** by project convention
> (Rule Zero — no busywork translation of internal documents).
