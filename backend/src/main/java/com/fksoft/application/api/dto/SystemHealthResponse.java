package com.fksoft.application.api.dto;

/**
 * Health payload returned by {@code GET /api/system/health}.
 *
 * @param status overall service status ({@code UP}/{@code DOWN})
 * @param db database connectivity status ({@code UP}/{@code DOWN})
 */
public record SystemHealthResponse(String status, String db) {}
