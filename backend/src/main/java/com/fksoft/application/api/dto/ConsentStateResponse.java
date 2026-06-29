package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.ConsentState;
import com.fksoft.domain.marketing.ConsentView;
import java.util.List;

/**
 * Response for {@code GET /api/marketing/consents?subject=&purpose=} (SPEC-0019): the current state
 * plus the full append-only history (DL-0056).
 *
 * @param current the current consent state (latest row projection)
 * @param history every row for the subject+purpose, newest first
 */
public record ConsentStateResponse(ConsentState current, List<ConsentView> history) {}
