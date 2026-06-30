package com.fksoft.domain.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * In-process event published on a successful login (SPEC-0024 Events). Carries no secret material —
 * only the user id, username and the instant (BR4). Consumed for access auditing.
 *
 * @param userId the authenticated user id
 * @param username the login
 * @param at when the login happened
 */
public record UserAuthenticated(UUID userId, String username, Instant at) {}
