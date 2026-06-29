# Frontend Architecture — Angular

> Read when: writing or changing any Angular code, UI behavior, forms, state, HTTP or
> real-time handling.

## Structure

Organize by feature/domain with controlled `core` and `shared`:

```txt
src/app
  core/      auth config http interceptors guards layout observability realtime
  shared/    components directives pipes validators utils
  features/
    orders/     pages components services models
    customers/  pages components services models
```

`core` = app-wide infrastructure and singletons. `shared` = genuinely reusable UI/utils.
Feature-specific code **MUST** stay inside the feature.

## Product and UX

Frontend is product in use. Decision priority: product requirements > UX spec > customer
expectations > approved designs/Figma > design system > acceptance criteria >
accessibility/responsiveness when required > existing patterns > Angular code organization.
Technical preference **MUST NOT** override product/UX requirements. UI represents the user
workflow, not the database model.

Screens handle real usage states when relevant: loading, empty, error, validation errors,
permission denied, partial data, operation in progress, success/failure feedback,
confirmation before destructive actions, disabled submit during processing.

Accessibility follows project requirements/contract (e.g. WCAG 2.1 AA when defined). Even
when not required, avoid free bad practices: proper buttons, form labels, native behavior.

## State management

Pragmatic and proportional. No global store by default: local component state for simple UI
state; feature services for state shared inside a feature; RxJS or Signals by clarity. NgRx
or similar only for real complexity (multi-feature state, offline, undo/redo, real-time
sync, complex permissions).

## Components and forms

Pages/feature components **MAY** orchestrate; reusable components are presentation-oriented
with inputs/outputs and minimal business coupling — use smart/dumb only when it clarifies.
Reactive Forms by default; template-driven for trivial cases. Large forms extract builders,
mappers and validators.

## HTTP and errors

Hybrid approach: `core/http` owns base URL, auth headers, correlation ID, global error
handling, retries/timeouts, interceptors, API error parsing. Each feature exposes
domain-oriented API services. No raw `HttpClient` scattered in components; no generic API
client hiding domain intention.

Error handling combines global normalization with feature-specific presentation (toast,
inline error, modal, error state, banner, retry, permission screen). Do not force every
error into a generic toast.

## Real-time

Only when the product justifies it. `core/realtime` manages connection concerns; feature
services translate technical events into domain/UI behavior. Components never handle raw
WebSocket/SSE protocol messages. Real-time contracts stable and versioned when they evolve.

## UI libraries and styling

Follow the existing project standard (Material, PrimeNG, Tailwind, custom design system) —
never casually mix libraries. Styling follows the project standard; component styles stay
close to the component; global styles **MUST NOT** become a dumping ground. User-facing text
always goes through the project i18n mechanism (labels, buttons, tables, empty states,
dialogs, toasts, enum display names, dates/numbers/currencies).
