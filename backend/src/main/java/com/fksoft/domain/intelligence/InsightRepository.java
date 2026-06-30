package com.fksoft.domain.intelligence;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for the {@link Insight} read-model. Module-internal. */
@ModuleInternal
public interface InsightRepository extends JpaRepository<Insight, UUID> {

  Optional<Insight> findByTypeAndSubjectKindAndSubjectRef(
      InsightType type, SubjectKind subjectKind, String subjectRef);

  /**
   * Lists insights with optional type/subjectRef/status filters, ordered by estimated gain
   * descending (prioritizes what is worth more, SPEC-0013 API Contracts; nulls last).
   */
  @Query(
      """
      select i from Insight i
      where (:type is null or i.type = :type)
        and (:subjectRef is null or i.subjectRef = :subjectRef)
        and (:status is null or i.status = :status)
      order by i.estimatedGainBrl desc nulls last, i.generatedAt desc
      """)
  Page<Insight> search(
      @Param("type") InsightType type,
      @Param("subjectRef") String subjectRef,
      @Param("status") InsightStatus status,
      Pageable pageable);
}
