# Observability and Performance

> Read when: adding/changing logs, metrics, tracing, health checks, or doing any
> performance-related work.

## Observability

Observability is architecture, not optional polish. Logs are structured, contextual and
safe; distinguish application flow, business events, integration, error, audit and security
logs. Logs **MUST** answer: what happened, when, which user/tenant/request/job/message,
which business entity, success or failure, duration, and failure class (validation,
business, infrastructure, integration, bug). Never log secrets.

Every relevant request, message, job and async flow **SHOULD** carry a correlation ID.

Expose Prometheus-compatible metrics when relevant; Grafana for production visibility.
Technical: request count/latency, error rate, JVM memory, CPU, threads, DB pool, queue size,
consumer lag, retries, timeouts, external API latency/failures. Business metrics when
useful. AI metrics: provider/model, latency, failures, invalid outputs, fallbacks,
confidence, human overrides. Health checks distinguish liveness/readiness. Alerts are
actionable.

## Performance

Avoid obviously bad choices from the start: N+1 queries, missing indexes, large object
graphs, unbounded queries, missing pagination, huge payloads, external calls in loops,
synchronous long-running work, missing timeouts, missing backpressure, screens loading too
much data.

Heavy optimization is evidence-driven only: logs, metrics, traces, profilers, execution
plans, slow query logs, load tests, production-like volumes. Do not introduce complex
caching, async processing, denormalization or distribution without reason. Code stays
readable unless a measured hotspot justifies complexity.
