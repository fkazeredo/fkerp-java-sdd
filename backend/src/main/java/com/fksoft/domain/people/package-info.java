/**
 * People module (SPEC-0012 operational side; redesign 7.8): the bounded context that owns the
 * <strong>operational</strong> point-clock data — the mirror snapshot collected from the vendor
 * portal for HR (journey, absences), explicitly marked non-legal ({@code operationalOnly = true},
 * BR3) so no consumer ever treats it as a retention document. The <strong>legal</strong> artifact
 * (the signed AFD/AEJ) is never here — it is a {@code Document} in the Compliance vault
 * (SPEC-0008).
 *
 * <p>This module is the owner of the collection use case and its idempotency (BR5, by {@code
 * (sourceRef, periodRef)}) and of the crawl-run execution history (BR7). The technical crawler (the
 * portal client, the queue and the circuit breaker) lives in {@code
 * com.fksoft.infra.integration.pointclock} (an ACL adapter, DL-0030/DL-0031); it drives this module
 * only through the public {@link com.fksoft.domain.people.PointSnapshotService} facade and never
 * writes into core aggregates (BR6).
 *
 * <p>Spring Modulith application module. Public API: the {@link
 * com.fksoft.domain.people.PointSnapshotService} use cases, the {@link
 * com.fksoft.domain.people.PointSnapshotView}/{@link com.fksoft.domain.people.PointCrawlRunView}
 * views, the {@link com.fksoft.domain.people.CollectSnapshotCommand} command, the {@link
 * com.fksoft.domain.people.PointSnapshotCollected}/{@link
 * com.fksoft.domain.people.PointCrawlingFailed} events, the {@link
 * com.fksoft.domain.people.PointFailureClass} classification and the business exceptions. The
 * {@code internal} sub-package (entities, repositories) is module-private (Spring Modulith verify).
 * It is a leaf module — nothing depends on it except the infra crawler — so it forms no dependency
 * cycle.
 */
@org.springframework.modulith.ApplicationModule(displayName = "People")
package com.fksoft.domain.people;
