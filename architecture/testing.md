# Testing

> Read when: writing or changing any test, or deciding test strategy for a change.

## Philosophy

Tests protect behavior, prevent regressions, make refactoring safer and document
expectations. Coverage is a signal, not the goal; high coverage with weak assertions is not
quality.

## Backend

Prioritize: domain rules, state transitions, invariants, Application Services, business
exceptions, validation, repositories/queries, transactions, locking, integration boundaries
and ACLs, messaging, outbox/inbox, API contracts, security-sensitive flows, real-time, AI
validation/fallback logic.

Unit tests cover domain/application logic without infrastructure. Integration tests cover
persistence, transactions, APIs, messaging — use Testcontainers when infrastructure behavior
matters. Architecture tests (ArchUnit / Spring Modulith `verify()`) run in the normal test
suite and **MUST NOT** be weakened to make code pass.

## Regression tests

Every bug fix **MUST** add a regression test whenever technically possible: it fails before
the fix and passes after. If impossible, explain why in the final response.

## Frontend

Protect real behavior: feature/API/state services, guards, interceptors, validators, form
builders/mappers, error handling, permission behavior, loading/empty/error states, submit
flows, real-time handling, critical user journeys. E2E for critical flows only.

## Test environments

Right level of realism: don't mock everything blindly; don't boot the whole stack for simple
logic. Mocks/fakes for logic and orchestration; Testcontainers for infrastructure behavior.
Separate unit, integration and E2E commands when possible. Tests create their own data via
builders/factories/fixtures.
