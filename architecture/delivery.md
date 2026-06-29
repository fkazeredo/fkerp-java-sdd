# Build, Delivery, Infrastructure, Flags and Audit

> Read when: touching build config, dependencies, Git workflow, CI/CD, Docker/deploy,
> feature flags or audit fields.

## Build and versions

Maven is the default for Java/Spring Boot; never migrate Maven<->Gradle without explicit
instruction and an ADR. Java/Spring Boot/Angular/Node versions prioritize stability and LTS;
no experimental/milestone/RC/snapshot dependencies in production. Dependencies are chosen
conservatively: standard capabilities first, mature libraries, acceptable licenses; risky
dependencies isolated; architecturally significant dependencies documented in ADRs. Do not
add libraries for trivial problems.

## Git

Pragmatic Git Flow (`main`, `develop`, `feature/*`, `bugfix/*`, `release/*`, `hotfix/*`) and
Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`). PRs are focused and reviewable,
including tests, specs, migrations, screenshots for UI changes, API impacts and ADR updates
when applicable. Semantic Versioning (`MAJOR.MINOR.PATCH`) is the official policy — see
**ADR 0015** for the per-digit criteria, reset rules, the `0.y.z` initial-development phase and
the mapping to ROADMAP phases / Conventional Commits. The version's source of truth is
`backend/pom.xml`; releases are tagged from `main`.

Generated files are never edited manually — modify the generation source (OpenAPI contract,
`.proto`, schema, generator config). Generated code separated from handwritten code (a
PreToolUse hook blocks edits under generated paths).

## CI/CD

A production-grade application has an automated pipeline: build, unit and integration tests,
frontend tests/build, lint/static analysis, dependency vulnerability scan, migration
validation, contract validation, image build, deployment, smoke tests, rollback strategy,
post-deploy observability checks. Failed tests, broken builds, invalid migrations or broken
contracts block merge/deploy.

## Local development and configuration

Reproducible local env: Docker Compose when external services are needed; minimal but
complete; `.env.example` provided; start/stop/test documented. Configuration is
externalized, typed and validated at startup; secrets never committed (env vars, secret
managers, vaults).

## Deployment and IaC

Use the infrastructure that fits the project/client. Business logic **MUST NOT** depend on
cloud SDKs or deployment-specific details — abstract storage, messaging and notifications
when variation is possible. Never create Terraform/Helm/K8s files unless the project context
requires it.

## Feature flags

Flags reduce delivery risk; they **MUST NOT** become permanent hidden complexity. Every flag
has: name, purpose, owner, scope, default, removal condition, and tests for both states when
business logic changes. Most flags are temporary; long-lived flags only for real product or
tenant configuration.

## Audit and history

Auditability proportional to business relevance. Relevant entities track createdAt,
updatedAt, createdBy, updatedBy. Important business actions audited in business language
(`OrderCancelled`, `ManualOverridePerformed`, `PermissionGranted`). Critical domains **MAY**
need richer history: state transitions, decisions, manual overrides, AI predictions,
operational timeline.
