# NNNN - Title

> Format: `NNNN-kebab-case-title.md` where NNNN is the next sequential number.
> Sections are normative — fill every applicable one. When a section truly does
> not apply, write "Not applicable." instead of deleting it, so reviewers know
> it was considered. "Open Questions" stays even when the answer is "None."

Status: Draft | Approved | Implemented
Related ADRs: —

## Goal

One paragraph: what business outcome this delivers.

## Scope

In scope and explicitly out of scope.

## Business Context

Why this matters, who the actors are, where it sits in the larger workflow.

## Business Rules

Direct, testable statements:

```txt
The system MUST reject X when Y.
The API MUST return 409 with error code z.cannot-z.
```

## Input/Output Examples

Concrete examples of meaningful requests/responses or domain transitions.

## API Contracts

Endpoint(s), method, request and response bodies, status codes, error codes,
authentication/authorization, pagination/filtering/sorting.

## Events

Name (business fact), payload, producer, consumers, contract stability,
after-commit guarantee when applicable.

## Persistence Changes

Tables, columns, constraints, indexes, locking, migration strategy.

## Validation Rules

At every relevant boundary: delivery (controller), application
(preconditions), domain (invariants), persistence (constraints), integration.

## Error Behavior

How errors surface to the caller, error codes, i18n keys, retry semantics.

## Observability Requirements

Logs (what fields), metrics (which), correlation ID propagation, audit when
business-relevant.

## Tests Required

Unit, integration, regression and architectural tests required.

## Acceptance Criteria

Concrete "this is done when..." list. Each item independently verifiable.

## Open Questions

Things explicitly NOT decided yet. The Code MUST ask before implementing any
behavior that depends on these. Write "None." if there is nothing to ask.

## Out of Scope

What this spec deliberately does NOT cover.
