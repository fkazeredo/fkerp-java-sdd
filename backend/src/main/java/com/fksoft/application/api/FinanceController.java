package com.fksoft.application.api;

import com.fksoft.application.api.dto.CreateLedgerEntryRequest;
import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.EntryStatus;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PeriodView;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
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
 * REST endpoints for the Finance module (SPEC-0015): create/confirm/list AP/AR ledger entries and
 * run the monthly close (which respects the Compliance veto). The delivery layer resolves the
 * acting user for audit and maps the {@code YYYY-MM} path to the period value object.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final FinanceService financeService;
  private final UserContextProvider userContextProvider;

  @PostMapping("/entries")
  public ResponseEntity<LedgerEntryView> create(
      @Valid @RequestBody CreateLedgerEntryRequest request) {
    LedgerEntryView view =
        financeService.register(
            request.direction(),
            new Party(request.party().id(), request.party().type()),
            request.amount(),
            request.entryType(),
            AccountingPeriodId.of(request.period()),
            actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/entries/{id}/confirm")
  public LedgerEntryView confirm(@PathVariable UUID id) {
    return financeService.confirm(id, actor());
  }

  @GetMapping("/entries/{id}")
  public LedgerEntryView getEntry(@PathVariable UUID id) {
    return financeService.getEntry(id);
  }

  @GetMapping("/entries")
  public PageResponse<LedgerEntryView> list(
      @RequestParam(required = false) LedgerDirection direction,
      @RequestParam(required = false) EntryStatus status,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String party,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<LedgerEntryView> result = financeService.list(direction, status, period, party, pageable);
    return PageResponse.from(result);
  }

  @PostMapping("/periods/{yyyymm}/close")
  public PeriodView close(@PathVariable String yyyymm) {
    return financeService.closePeriod(AccountingPeriodId.of(yyyymm), actor());
  }

  @GetMapping("/periods/{yyyymm}")
  public PeriodView getPeriod(@PathVariable String yyyymm) {
    return financeService.getPeriod(AccountingPeriodId.of(yyyymm));
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
