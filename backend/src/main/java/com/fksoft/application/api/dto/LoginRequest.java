package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request (SPEC-0024 — {@code POST /api/identity/login}). The password is never logged (BR4).
 *
 * @param username the login
 * @param password the raw password
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
