package com.fksoft.application.api;

import com.fksoft.application.api.dto.CreatePayoutRequest;
import com.fksoft.application.api.dto.ExecutePayoutRequest;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.infra.integration.payment.PayoutExecutionService;
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
 * REST endpoints for the Payout module (SPEC-0017): create a repass/settlement/refund (with an
 * optional installment plan), execute it (or its next installment), and read/list payouts. The
 * execution goes through the {@code PayoutExecutionService} orchestrator (infra) which wires the
 * Payout facade, the payment ACL and the Compliance receipt; create/read go straight to the {@link
 * PayoutService} domain facade. The delivery layer resolves the acting user for audit.
 */
@Tag(name = "Payout", description = "Repasse ao agente, liquidação e reembolso")
@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final PayoutService payoutService;
  private final PayoutExecutionService executionService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<PayoutView> create(@Valid @RequestBody CreatePayoutRequest request) {
    CreatePayoutCommand command =
        new CreatePayoutCommand(
            request.kind(),
            new Payee(request.payee().id(), request.payee().type()),
            request.bookingId(),
            request.originRef(),
            request.amount(),
            request.settlementRate(),
            request.installmentCount(),
            request.dueDates(),
            request.amounts());
    PayoutView view = payoutService.create(command, actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/{id}/execute")
  public PayoutView execute(
      @PathVariable UUID id, @RequestBody(required = false) ExecutePayoutRequest request) {
    PaymentOutcome outcomeHint = request == null ? null : request.outcomeHint();
    return executionService.execute(id, outcomeHint);
  }

  @GetMapping("/{id}")
  public PayoutView getById(@PathVariable UUID id) {
    return payoutService.getById(id);
  }

  @GetMapping
  public PageResponse<PayoutView> list(
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) PayoutStatus status,
      @RequestParam(required = false) String payee,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PayoutView> result = payoutService.list(kind, status, payee, pageable);
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
