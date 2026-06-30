package com.fksoft.application.api;

import com.fksoft.application.api.dto.CreateEmployeeRequest;
import com.fksoft.domain.people.EmployeeStatus;
import com.fksoft.domain.people.EmployeeView;
import com.fksoft.domain.people.PeopleService;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
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
 * REST endpoints for the People HR module (SPEC-0022): register/read/list collaborators. The
 * journey and time-bank read endpoints are added in slice 8i-2 ({@code PeopleJourneyController}).
 * All calls go straight to the {@link PeopleService} domain facade; the delivery layer resolves the
 * acting user for audit.
 */
@RestController
@RequestMapping("/api/people/employees")
@RequiredArgsConstructor
public class PeopleController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final PeopleService peopleService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<EmployeeView> register(@Valid @RequestBody CreateEmployeeRequest request) {
    EmployeeView view = peopleService.register(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/{id}")
  public EmployeeView getById(@PathVariable UUID id) {
    return peopleService.getById(id);
  }

  @GetMapping
  public PageResponse<EmployeeView> list(
      @RequestParam(required = false) EmployeeStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<EmployeeView> result =
        peopleService.list(status, PageRequest.of(Math.max(page, 0), clampSize(size)));
    return PageResponse.from(result);
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
