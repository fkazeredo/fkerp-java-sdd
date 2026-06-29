# Simulation, Mocking and Deferral

> Read when: a task touches a requirement that is **out of scope**, **undecided**, or owned by
> a future spec, and you are tempted to either build it now or change a spec to make the gap
> go away.

Normative language follows `core-principles.md`: **MUST**, **MUST NOT**, **SHOULD**, **MAY**.

## The rule

When the current milestone meets a requirement that is **not yet in scope** or **not yet
decided**, Claude Code **MUST NOT** expand scope to satisfy it and **MUST NOT** rewrite a spec
to force a resolution. Instead, **simulate or mock the seam** and **wait for the implementation
that harmonizes it** — the spec that actually owns that behavior.

This is the operational form of Rule Zero (`core-principles.md`: *current business need over
speculative future need*) and of the spec workflow (`workflow.md`: specs are living artifacts;
Open Questions are **registered, not invented away**).

## What "simulate / mock" means here

A simulation or mock is an **explicit, traceable placeholder** that stands in for a behavior
whose real implementation belongs to a later spec. It is:

- **Explicit** — named as a stand-in (test double, `@TestConfiguration` stub, seed data, a
  fake adapter behind a port), never a silent hard-coded value pretending to be real logic.
- **Traceable** — references the owning spec (`SPEC-XXXX`) so it can be found and replaced.
- **Narrow** — covers only the seam needed by the current milestone; it does **NOT** grow into
  a speculative framework, facade or API.
- **Replaceable** — disappears (or graduates into real code) when the owning spec is built.

A mock **MUST NOT** produce misleading business results in production paths
(`messaging-and-integrations.md`: fallbacks must not silently fake business outcomes). Mocking
is for **tests and deferred seams**, not for shipping fake behavior to users.

## What you MUST NOT do instead

- **MUST NOT** build the out-of-scope feature "while you're here" (no speculative CRUD, facade,
  read-API, queue, cache or config for a consumer that does not exist yet).
- **MUST NOT** edit a spec's scope, business rules or Open Questions to close a gap the current
  milestone does not own. Surface it; leave the Open Question open for its spec.
- **MUST NOT** invent business behavior to fill the gap (`CLAUDE.md` invariant 3).

## How to defer correctly

1. Ship the **smallest correct seam** the current spec needs (entity, table, seed, port).
2. If a future module will consume it, leave it **queryable / pluggable**, but add the public
   API, facade or consumer-side code **only when its owning spec arrives** — that spec
   harmonizes the seam.
3. Record the deferral where it belongs: the owning spec's **Open Questions**, an ADR if the
   boundary is architectural, or a test double annotated with the `SPEC-XXXX` that will replace
   it. Do not record it by mutating an unrelated spec.

## Worked example (SPEC-0007)

The cinema module ships rooms and seats as **seed-managed** data (no REST). Room/seat admin
CRUD and the cross-module seat read-API are **out of scope** and owned by later specs (CRUD: a
future spec; read access: SPEC-0011). Per this rule the milestone ships only the schema, seed
and entities; it adds **no facade and no CRUD**, and it leaves SPEC-0007's Open Question open
rather than resolving it in the spec. The future specs harmonize those seams.
