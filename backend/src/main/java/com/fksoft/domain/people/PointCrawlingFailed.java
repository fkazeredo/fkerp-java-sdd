package com.fksoft.domain.people;

import java.time.Instant;

/**
 * Business fact: a point-crawl attempt failed (SPEC-0012 BR2). Published in-process when the crawl
 * cannot produce a snapshot; observability/alerting (the Platform side) consumes it. It never
 * carries a fake business result — a failure is a failure, classified, not a misleading success.
 *
 * @param sourceRef the REP/branch reference
 * @param failureClass the failure classification (TIMEOUT, UNAVAILABLE, AUTHENTICATION_FAILED, ...)
 * @param deadLettered whether the run was dead-lettered (attempts exhausted or fatal class)
 * @param occurredAt when the failure occurred
 */
public record PointCrawlingFailed(
    String sourceRef, PointFailureClass failureClass, boolean deadLettered, Instant occurredAt) {}
