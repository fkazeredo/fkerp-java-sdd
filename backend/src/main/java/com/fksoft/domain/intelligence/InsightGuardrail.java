package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;

/**
 * The guardrail an insight may carry (SPEC-0013 BR1/BR3): the limit that was crossed, if any. It is
 * an ALERT that highlights the insight — it NEVER blocks the operation (BR3).
 *
 * @param description what limit was crossed (human-readable)
 * @param thresholdCrossed the threshold value that was crossed (BRL)
 */
public record InsightGuardrail(String description, Money thresholdCrossed) {}
