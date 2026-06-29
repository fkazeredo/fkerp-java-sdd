package com.fksoft.application.api;

import com.fksoft.application.api.dto.SettlementRequest;
import com.fksoft.domain.reconciliation.CaseStatus;
import com.fksoft.domain.reconciliation.ReconciliationCaseView;
import com.fksoft.domain.reconciliation.ReconciliationService;
import com.fksoft.domain.reconciliation.SettlementInput;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for reconciliation (SPEC-0007). Cases are not created by API (they are born from
 * {@code BookingConfirmed}); the API reads them (ordered by discrepancy) and records the realized
 * settlement.
 */
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final ReconciliationService reconciliationService;
  private final UserContextProvider userContextProvider;

  @GetMapping("/{caseId}")
  public ReconciliationCaseView get(@PathVariable UUID caseId) {
    return reconciliationService.getById(caseId);
  }

  @GetMapping
  public PageResponse<ReconciliationCaseView> list(
      @RequestParam(required = false) CaseStatus status,
      @RequestParam(required = false) BigDecimal minDiscrepancy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<ReconciliationCaseView> result =
        reconciliationService.list(status, minDiscrepancy, pageable);
    return PageResponse.from(result);
  }

  @PostMapping("/{caseId}/settlement")
  public ReconciliationCaseView settle(
      @PathVariable UUID caseId, @Valid @RequestBody SettlementRequest request) {
    String actor = userContextProvider.currentUser().username();
    SettlementInput input =
        new SettlementInput(
            request.amountReceivedFromAgency(),
            request.supplierSettlementRate(),
            request.supplierPaidAmount(),
            request.commissionReceivedFromSupplier(),
            request.commissionPaidToAgent());
    return reconciliationService.recordSettlement(caseId, input, actor);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
