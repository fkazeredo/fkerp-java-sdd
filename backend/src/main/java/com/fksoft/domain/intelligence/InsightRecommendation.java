package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;

/**
 * The recommendation an insight carries (SPEC-0013 BR1): a verdict, the human-readable action, and
 * the estimated gain and/or risk in Money. It SUGGESTS — the human pulls the trigger (BR2/8.3).
 *
 * @param verdict the advisor verdict (CONVERTE/QUEIMA_MARGEM)
 * @param action the suggested action, human-readable
 * @param estimatedGain the estimated gain of following the advice (BRL), or {@code null}
 * @param estimatedRisk the estimated risk of not acting (BRL), or {@code null}
 */
public record InsightRecommendation(
    Verdict verdict, String action, Money estimatedGain, Money estimatedRisk) {}
