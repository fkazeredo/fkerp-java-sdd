package com.fksoft.application.api;

import com.fksoft.application.api.dto.ProcessJourneyRequest;
import com.fksoft.domain.people.DiscrepancyStatus;
import com.fksoft.domain.people.DiscrepancyView;
import com.fksoft.domain.people.JourneyView;
import com.fksoft.domain.people.PeopleService;
import com.fksoft.domain.people.TimeBankView;
import com.fksoft.infra.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the People HR journey/time-bank/discrepancy use cases (SPEC-0022 BR2/BR3/BR4):
 * process and read a collaborator period journey, read the time-bank and browse the discrepancy
 * treatment queue. All calls go straight to the {@link PeopleService} domain facade.
 */
@Tag(name = "People Journey", description = "Jornada e banco de horas")
@RestController
@RequestMapping("/api/people")
@RequiredArgsConstructor
public class PeopleJourneyController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final PeopleService peopleService;

  @PostMapping("/employees/{id}/journey")
  public ResponseEntity<JourneyView> processJourney(
      @PathVariable UUID id, @Valid @RequestBody ProcessJourneyRequest request) {
    JourneyView view = peopleService.processJourney(request.toCommand(id));
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/employees/{id}/journey")
  public JourneyView journey(@PathVariable UUID id, @RequestParam String period) {
    return peopleService.getJourney(id, period);
  }

  @GetMapping("/employees/{id}/timebank")
  public TimeBankView timebank(@PathVariable UUID id, @RequestParam String period) {
    return peopleService.getTimeBank(id, period);
  }

  @GetMapping("/discrepancies")
  public PageResponse<DiscrepancyView> discrepancies(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) DiscrepancyStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<DiscrepancyView> result =
        peopleService.listDiscrepancies(
            period, status, PageRequest.of(Math.max(page, 0), clampSize(size)));
    return PageResponse.from(result);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
