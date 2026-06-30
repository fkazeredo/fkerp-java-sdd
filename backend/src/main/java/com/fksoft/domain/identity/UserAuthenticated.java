package com.fksoft.domain.identity;

import java.time.Instant;

/**
 * In-process event published on a user's first authenticated touch in a session (SPEC-0024 Events).
 * Since Phase 13 the login itself happens at the external IdP (DL-0104); this event marks that an
 * already-authenticated principal was first seen by the ERP, backing the {@code
 * acme.identity.logins} business metric (DL-0098) and the {@code AUTH_LOGIN} access audit
 * (DL-0083). Carries metadata only — never a token/secret (BR4).
 *
 * @param userId the stable user id (IdP subject), or {@code null} when unresolved
 * @param username the login (preferred_username)
 * @param at when the first touch happened
 */
public record UserAuthenticated(String userId, String username, Instant at) {}
