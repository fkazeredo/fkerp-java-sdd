package com.fksoft.domain.intelligence;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of an {@code Insight} (SPEC-0013): the prescriptive advice the human reads, with
 * its evidence (numbers + provenance), recommendation (action + gain/risk), the guardrail crossed
 * (if any), and the human-decision status. It is a projection — reading it changes nothing.
 *
 * @param id the insight id
 * @param type the insight-type cadastro code (was {@code InsightType}; SPEC-0031/DL-0116)
 * @param subjectKind the axis cadastro code (was {@code SubjectKind}; AGENCY in v1)
 * @param subjectRef the subject reference (e.g. the agency/account id)
 * @param evidence the numbers and their provenance (BR1)
 * @param recommendation the verdict, action and estimated gain/risk (BR1)
 * @param guardrail the limit crossed (alert), or {@code null} (BR3)
 * @param status the human-decision status (BR4)
 * @param generatedAt when the insight was generated
 * @param decidedBy who decided (or {@code null} while NEW) (BR4)
 * @param decidedAt when the decision was recorded (or {@code null}) (BR4)
 */
public record InsightView(
    UUID id,
    String type,
    String subjectKind,
    String subjectRef,
    InsightEvidence evidence,
    InsightRecommendation recommendation,
    InsightGuardrail guardrail,
    InsightStatus status,
    Instant generatedAt,
    String decidedBy,
    Instant decidedAt) {}
