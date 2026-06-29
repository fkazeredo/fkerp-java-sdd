# Persistence, Data and Caching

> Read when: touching the database, migrations, transactions, locking, deletion, reports,
> search/filters, or caching.

## Database and migrations

Database design is architecture. Flyway is the migration tool: every schema change is a
versioned SQL migration. Never rely on `ddl-auto=update` in production. The database enforces
integrity: PKs, FKs, unique/not-null/check constraints, indexes, isolation, locks, views.
Slow queries and missing indexes are architecture problems.

Data migrations/backfills: a plain Flyway migration is enough for simple changes. When a
migration affects production data, large tables, critical workflows or compatibility, the
spec/ADR defines the strategy (backfill, expand/contract, background migration, window).

Seeds: essential system data **MAY** go through Flyway; local dev fake data **MUST NOT** mix
with production migrations; tests create their own data via builders/factories/fixtures.

## Transactions and consistency

`@Transactional` at the Application Service method representing the use case. Never pretend
a message publish or external call is atomic with a DB commit. Eventual consistency only
with safeguards: idempotency keys, retries, DLQs, outbox/inbox, locking, versioning,
compensating actions, status machines, reconciliation jobs, audit trail, correlation IDs,
metrics, explicit failure states.

## Concurrency and locking

Optimistic locking (`@Version`) is the default for mutable entities at risk. Pessimistic
locking when risk justifies: financial operations, stock, critical transitions, outbox
processing, job claiming. Concurrency conflicts are translated into clear API/domain errors,
never raw database exceptions.

## Deletion

Strategy by business relevance: hard delete for disposable data; soft delete to preserve
records out of normal usage; lifecycle status when deletion has business meaning. Important
deletion-like actions **SHOULD** be audited.

## Reports and heavy reads

Never force every read through domain aggregates. Use query services, projections, DTOs,
SQL, views, materialized views, read models or denormalized tables as appropriate. Large
reports run asynchronously with status, file generation, download link, logs and history.

## Search and filters

Relational database first. Search engines (Elasticsearch/OpenSearch/Meilisearch) only when
requirements justify operational cost. Search endpoints define: filters, searchable/sortable
fields, pagination model, default sort, max page size, empty-result behavior, case
sensitivity, partial match, date/timezone behavior, errors.

## Caching

Cache only when it solves a real problem. Every cache defines: key, value, expiration,
invalidation, expected consistency, max staleness, scope (local/distributed/user/tenant),
metrics, failure behavior, memory impact, serialization. Track hit/miss rate, evictions,
size, latency, errors. Stale data is an architectural trade-off, not an accident.
