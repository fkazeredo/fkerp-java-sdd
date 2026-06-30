package com.fksoft.application.api.dto;

/**
 * Login response (SPEC-0024 — {@code POST /api/identity/login}): the bearer token and the resolved
 * user. The frontend stores the token and sends it as {@code Authorization: Bearer <token>}.
 *
 * @param token the signed JWT
 * @param tokenType always {@code Bearer}
 * @param expiresIn token lifetime in seconds
 * @param user the authenticated user (id/username/roles)
 */
public record LoginResponse(String token, String tokenType, long expiresIn, MeResponse user) {}
