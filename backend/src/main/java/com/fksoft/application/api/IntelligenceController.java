package com.fksoft.application.api;

import com.fksoft.domain.intelligence.InsightStatus;
import com.fksoft.domain.intelligence.InsightType;
import com.fksoft.domain.intelligence.InsightView;
import com.fksoft.domain.intelligence.IntelligenceService;
import com.fksoft.infra.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Intelligence (DSS) module (SPEC-0013). Insights are not created by API —
 * they are born from consumed events. The API reads them (ordered by estimated gain, prioritizing
 * what is worth more). There is, by principle, NO endpoint that makes the DSS act on the operation
 * (the decision endpoint in slice 12b only records the human decision).
 */
@RestController
@RequestMapping("/api/intelligence/insights")
@RequiredArgsConstructor
public class IntelligenceController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final IntelligenceService intelligenceService;

  @GetMapping("/{insightId}")
  public InsightView get(@PathVariable UUID insightId) {
    return intelligenceService.getById(insightId);
  }

  @GetMapping
  public PageResponse<InsightView> list(
      @RequestParam(required = false) InsightType type,
      @RequestParam(required = false) String subjectRef,
      @RequestParam(required = false) InsightStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<InsightView> result = intelligenceService.list(type, subjectRef, status, pageable);
    return PageResponse.from(result);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
