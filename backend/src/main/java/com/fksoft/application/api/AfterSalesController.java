package com.fksoft.application.api;

import com.fksoft.application.api.dto.OpenCaseRequest;
import com.fksoft.application.api.dto.ResolveCaseRequest;
import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.aftersales.OpenCaseCommand;
import com.fksoft.domain.aftersales.ResolveCaseCommand;
import com.fksoft.domain.aftersales.SupportCaseStatus;
import com.fksoft.domain.aftersales.SupportCaseView;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * REST endpoints for the AfterSales module (SPEC-0018): open a case, drive its lifecycle
 * (assign/progress/wait/close), resolve it (which may trigger a Booking cancellation and/or a
 * Payout REFUND), and read/list cases. All transitions go straight to the {@link AfterSalesService}
 * domain facade, which owns the orchestration. The delivery layer resolves the acting user for
 * audit.
 */
@Tag(
    name = "AfterSales",
    description = "Pós-venda: chamados, SLA e resolução (reembolso/cancelamento)")
@RestController
@RequestMapping("/api/aftersales/cases")
@RequiredArgsConstructor
public class AfterSalesController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final AfterSalesService afterSalesService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<SupportCaseView> open(@Valid @RequestBody OpenCaseRequest request) {
    SupportCaseView view =
        afterSalesService.open(
            new OpenCaseCommand(request.bookingId(), request.type(), request.summary()), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/{id}/assign")
  public SupportCaseView assign(@PathVariable UUID id) {
    return afterSalesService.transition(id, SupportCaseStatus.IN_PROGRESS, actor());
  }

  @PostMapping("/{id}/progress")
  public SupportCaseView progress(@PathVariable UUID id) {
    return afterSalesService.transition(id, SupportCaseStatus.IN_PROGRESS, actor());
  }

  @PostMapping("/{id}/wait")
  public SupportCaseView wait(@PathVariable UUID id) {
    return afterSalesService.transition(id, SupportCaseStatus.WAITING, actor());
  }

  @PostMapping("/{id}/resolve")
  public SupportCaseView resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolveCaseRequest request) {
    return afterSalesService.resolve(
        id,
        new ResolveCaseCommand(
            request.resolution(),
            request.amount(),
            request.handlingCost(),
            request.serviceStartsAt(),
            request.cancellationReason()),
        actor());
  }

  @PostMapping("/{id}/close")
  public SupportCaseView close(@PathVariable UUID id) {
    return afterSalesService.transition(id, SupportCaseStatus.CLOSED, actor());
  }

  @GetMapping("/{id}")
  public SupportCaseView getById(@PathVariable UUID id) {
    return afterSalesService.getById(id);
  }

  @GetMapping
  public PageResponse<SupportCaseView> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) SupportCaseStatus status,
      @RequestParam(required = false) String bookingId,
      @RequestParam(required = false) Boolean breached,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "openedAt"));
    Page<SupportCaseView> result =
        afterSalesService.list(type, status, bookingId, breached, pageable);
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
