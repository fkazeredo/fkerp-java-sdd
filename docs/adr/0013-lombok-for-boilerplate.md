# ADR 0013: Lombok for boilerplate (not for entity mutation)

## Status

Accepted (owner decision)

## Context

The codebase carried mechanical boilerplate that adds no design value: manual SLF4J loggers
(`private static final Logger log = LoggerFactory.getLogger(X.class)`) in ~24 components, and
hand-written constructor-injection constructors (`this.x = x` over `final` fields) in dozens of
services/adapters. Entities also hand-wrote plain accessors. The owner wants this reduced
pragmatically (Rule Zero: cut the cost of change), without weakening the project's domain
integrity rules — entities must keep guarding their invariants and **must not** gain
uncontrolled setters (previously enforced by the ArchUnit `noLombokDataOnEntities` rule and the
"no `@Data`" invariant).

A wrinkle shapes the scope: the codebase uses **fluent accessors** (`movie.title()`, not
`getTitle()`), and DTOs are **records** (Lombok adds nothing there).

## Decision

Adopt Lombok, scoped to boilerplate:

- **Components:** `@Slf4j` for loggers; `@RequiredArgsConstructor` for constructor injection
  (over `final` fields; no field `@Autowired`). Constructors that carry `@Value`/`@Qualifier`
  or hold logic stay hand-written.
- **Entities:** `@Getter` + `@NoArgsConstructor(access = PROTECTED)` for accessor/JPA-ctor
  boilerplate. Business methods, computed/boolean accessors and public constructors stay
  hand-written.
- **Never `@Data` or `@Setter` on entities** — they mutate only through meaningful business
  methods. Enforced by two ArchUnit rules: `noLombokDataOnEntities` and the new
  `noLombokSetterOnEntities`.
- **Config** (`backend/lombok.config`): `lombok.accessors.fluent = true` (so `@Getter`
  generates `title()`, matching the convention and leaving call sites unchanged);
  `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value`;
  `lombok.addLombokGeneratedAnnotation = true`; `config.stopBubbling = true`. The dependency is
  `optional` (version managed by the Spring Boot parent) and excluded from the repackaged jar.

## Consequences

Positive: ~24 logger fields and dozens of DI constructors removed; entity accessor boilerplate
removed; the domain-integrity guarantee is stronger than before (no-setter is now executable,
not just prose). The change is behavior-preserving — the full battery passed unchanged (backend
211 tests + ArchUnit/Modulith/Spotless/Checkstyle; frontend lint + 25 test files + build).

Negative: a new compile-time dependency and an annotation-processor step; `lombok.accessors.fluent`
is a global setting (acceptable — only entities use `@Getter`; components use
`@Slf4j`/`@RequiredArgsConstructor`, DTOs are records, so no JavaBean-getter code exists to
break). Mixing `@Value` with `@RequiredArgsConstructor` is a known gotcha, so those constructors
are deliberately left explicit rather than forced through the `copyableAnnotations` path.

## Alternatives Considered

- **No Lombok (status quo).** Rejected by the owner: the boilerplate is pure cost.
- **Full Lombok including `@Data`/`@Setter` on entities.** Rejected: it breaks invariant
  protection and reintroduces uncontrolled mutation — the exact thing the entity rules forbid.
- **`@RequiredArgsConstructor` everywhere including `@Value` constructors.** Rejected: mixing
  `@Value` fields with generated constructors is fragile; those few constructors stay explicit.
