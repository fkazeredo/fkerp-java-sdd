# Core Principles

> Read when: making any non-trivial design decision, resolving conflicts, or considering
> an exception to the guideline.

Normative language: **MUST** = mandatory, **MUST NOT** = forbidden, **SHOULD** = recommended
default, **MAY** = allowed when justified.

## Ownership and decision bias

This guideline is intentionally opinionated. The architecture owner is the person
maintaining and applying it. Claude Code **MUST** favor the owner's explicit direction over
peer preference, market fashion, cargo-cult patterns or vague "best practice" claims.

```txt
owner instruction   over  peer preference
explicit spec       over  informal opinion
ADR                 over  undocumented convention
working simple design over architectural theater
current business need over speculative future need
maintainability     over  speed theater
```

## Philosophy

1. **Maintainability-first** — architecture exists to reduce the cost of change.
2. **Pragmatic DDD** — business rules, workflows, invariants and language explicit in code.
3. **Clean/Hexagonal as a principle** — protect core logic from unstable external concerns;
   never as folder theater.
4. **Modular Monolith First** — clear modular boundaries; microservices only with a concrete
   technical/organizational reason.

## Source of truth

Authority order: current owner request > feature spec > ADR > this guideline > existing code.

When existing code contradicts a spec/ADR/rule, Claude Code **MUST** identify the divergence
and reconcile deliberately: is the code outdated? the spec outdated? the ADR stale? is an
exception needed? is the request an intentional change? Claude Code **MUST NOT** blindly
rewrite code just because it differs, and **MUST NOT** silently invent behavior.

## Exceptions

Exceptions are allowed for real reasons only: explicit owner decision, current request, spec
or client requirement, regulatory/contractual constraint, legacy constraint, documented
project convention, framework limitation, measured performance need, operational/deployment
constraint, security/compliance, migration strategy, or deadline accepted by the owner.

Exceptions **MUST NOT** exist because a peer prefers another style or a tool generated it.
Exceptions **MUST NOT** be silent: if architecture, persistence, integration, messaging,
security, deployment, module boundaries or long-term maintainability are affected, document
in an ADR. An exception **MUST NOT** become a new default unless the owner updates the
guideline or accepts an ADR.

## Before adding complexity, always ask

```txt
Does this solve a real problem?         Is the requirement in the spec?
Is this proportional to the risk?       Can this be simpler?
Is there an existing project pattern?   Do tests protect the behavior?
Does this require an ADR?               Is this preserving owner direction?
```

Default mindset: spec first, domain first, clarity first, production awareness always,
overengineering never.
