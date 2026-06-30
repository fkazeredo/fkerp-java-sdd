package com.fksoft.domain.identity;

import java.time.Instant;

/**
 * In-process event published when an authenticated request is denied for insufficient role
 * (SPEC-0024 Events). Carries the actor, the attempted action and resource and the instant — never
 * a token/secret (BR4). Consumed for access auditing.
 *
 * @param actor who (the username, or {@code null} when unauthenticated)
 * @param action the attempted action (HTTP method + path template)
 * @param resource the targeted resource (request URI), or {@code null}
 * @param at when the denial happened
 */
public record AccessDenied(String actor, String action, String resource, Instant at) {}
