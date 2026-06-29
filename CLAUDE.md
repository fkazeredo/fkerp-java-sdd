# CLAUDE.md — Operating Rules

This file is always loaded. It contains only the rules that apply to EVERY task.
Detailed guidelines live in `architecture/` and are loaded on demand (see Routing Map).

## Non-negotiable invariants

1. **Rule Zero: avoid overengineering.** Architecture must reduce complexity. Patterns,
   layers, abstractions, queues, caches and interfaces exist only when they solve a real
   problem. A simple CRUD stays simple. When in doubt, choose the simplest solution that
   satisfies the spec and tests.
2. **Authority order (conflict resolution):**
   current owner/user request > feature spec > ADRs > `architecture/` docs > existing code.
   Existing code is evidence, not authority. Peer preference, fashion and undocumented
   convention are NOT sources of truth. Never silently invent behavior to resolve conflicts —
   surface the divergence and ask.
3. **Never invent business rules.** If missing information affects behavior, contracts,
   data, security or architecture: ASK before implementing. Record unresolved questions
   under `Open Questions` in the relevant spec.
4. **Spec-driven development.** Before relevant work: read the applicable spec in
   `docs/specs`; update it if the requirement changed; create one if none exists.
   Specs are living artifacts.
5. **Tooling is authoritative.** ArchUnit tests, Spotless, Checkstyle and CI gates encode
   architecture rules. Never weaken, skip or delete them to make code pass. If a rule seems
   wrong, propose a change and an ADR — do not bypass.
6. **No loose ends.** No TODOs/FIXMEs without an issue/spec/ADR reference, no commented-out
   code, no incomplete implementations, no `@Data`/`@Setter` on JPA entities (Lombok
   `@Getter`/`@RequiredArgsConstructor`/`@Slf4j` are welcome for boilerplate), no `*Impl`
   naming, constructor injection only.

## Definition of Done (every meaningful change)

- Code matches the spec; spec updated if the requirement changed.
- Tests created/updated. Bug fix => regression test (fails before, passes after); if
  impossible, explain why.
- Flyway migration when schema changes. OpenAPI/docs updated when contracts change.
- i18n messages added for any user-facing text. Global error handling respected.
- ADR created/updated when architecture changes.
- Build and tests executed when possible. Never hide failed commands or skipped checks.

## Final response after implementation

Report: files changed, behavior implemented, specs/ADRs updated, tests, migrations,
contract impacts, commands executed, verification result, risks and pending items.

## Routing Map — read BEFORE touching the area

| If the task involves... | Read first |
|---|---|
| Any non-trivial design decision | `architecture/core-principles.md` |
| Requirement out of scope/undecided; stubbing a future seam | `architecture/simulation-and-mocking.md` |
| Specs, ADRs, plans, large tasks | `architecture/workflow.md` |
| Backend code, services, entities, DTOs, errors, dates, naming | `architecture/backend.md` |
| Module boundaries, cross-module calls, microservices, BFF, repos layout | `architecture/modules-and-apis.md` |
| REST/GraphQL/gRPC endpoints, JSON contracts, OpenAPI | `architecture/modules-and-apis.md` |
| Events, queues, jobs, schedulers, idempotency, outbox | `architecture/messaging-and-integrations.md` |
| External APIs, files/uploads, notifications, AI/LLM/ML | `architecture/messaging-and-integrations.md` |
| Database, migrations, transactions, locking, caching, search | `architecture/persistence.md` |
| Security, authorization, user context, LGPD, multi-tenancy | `architecture/security.md` |
| Logs, metrics, tracing, performance | `architecture/observability.md` |
| Angular code, components, forms, state, UI | `architecture/frontend-angular.md` |
| Writing or changing tests | `architecture/testing.md` |
| Build, dependencies, Git, CI/CD, Docker, deploy, feature flags | `architecture/delivery.md` |
| Creating a new project from this template | `architecture/workflow.md` (section: New Project) |

## Project commands

Use official project commands; inspect `README.md`, `pom.xml`, `package.json`, `Makefile`
before inventing any. No system Maven — always use the wrapper from `backend/`:

```bash
cd backend && ./mvnw verify           # backend build + tests (ArchUnit; needs Docker up)
cd backend && ./mvnw spotless:apply   # format
npm run lint && npm test              # frontend (from spec 0002)
```

Destructive operations are governed by `.claude/settings.json` permissions. Do not attempt
to work around a denied command; explain the risk and ask the user to run it themselves.
