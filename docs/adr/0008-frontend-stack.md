# ADR 0008: Frontend Stack — Angular 22, PrimeNG, Tailwind, ngx-translate

## Status

Accepted

## Context

The frontend has three personas with different needs:

- **Customer**: visual product — screenings list, seat map, reservation
  flow. UX matters; the seat map is a custom interactive UI.
- **Operator**: functional terminal — lookup tickets and reprint. Few
  screens, no production.
- **Admin**: CRUD-heavy screens for movies, screenings, users. Standard
  enterprise UI.

Two language requirements: PT-BR (default) and EN. Switchable at runtime.

## Decision

- **Angular 22** with **standalone components** and **signals**, no NgModules,
  no NgRx. Local state via signals; cross-feature state via feature
  services with signals.
- **PrimeNG** for ready-made components: DataTable (sort/filter/pagination
  for admin CRUDs), Calendar with `pt-BR` locale, Dialog, Toast, OverlayPanel,
  ConfirmDialog. Reduces UI scaffolding cost significantly.
- **Tailwind CSS** for custom layout and the seat map grid (a perfect fit
  for utility classes). Coexists fine with PrimeNG; PrimeNG components are
  styled by PrimeNG themes, Tailwind applies to layout containers.
- **ngx-translate** for i18n with runtime language switch. Build-time
  Angular i18n was rejected because it requires one build per locale.
- Local proxy (`proxy.conf.json`) for `/api` and `/ws` to the backend during
  dev.

## Consequences

Positive: admin screens reach functional quickly thanks to PrimeNG. Seat map
benefits from Tailwind. Customer-facing pages can mix both. Theme switching
between light/dark possible via PrimeNG themes.

Negative: two CSS approaches live side by side. The team must learn which is
authoritative for what (PrimeNG owns component internals; Tailwind owns
layout and custom widgets). Document this in the frontend code style guide.
Bundle size larger than minimum (Tailwind purges unused; PrimeNG components
are tree-shaken when imported individually).

## Alternatives Considered

- **Angular Material only**: rejected. Material is opinionated visually, has
  fewer batteries for admin CRUDs than PrimeNG (no DataTable with built-in
  filtering, less rich Calendar locale, no first-class confirm dialog API).
- **Tailwind only with hand-built components**: rejected. The admin
  CRUDs would multiply the workload without product return.
- **PrimeNG only**: rejected. The seat map and other custom layouts gain a
  lot from utility-first CSS.
