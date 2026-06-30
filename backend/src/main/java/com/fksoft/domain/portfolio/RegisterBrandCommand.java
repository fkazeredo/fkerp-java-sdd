package com.fksoft.domain.portfolio;

/**
 * Command to register a represented brand (SPEC-0020 BR1).
 *
 * @param brandRef the unique brand/supplier identifier (value)
 * @param displayName the human-readable name
 */
public record RegisterBrandCommand(String brandRef, String displayName) {}
