package com.fksoft.application.api;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.domain.accounts.AccountService;
import com.fksoft.domain.accounts.AccountStatus;
import com.fksoft.domain.accounts.AccountView;
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
 * REST endpoints for commercial accounts (SPEC-0002). The delivery layer owns the {@code
 * UserContext}: it resolves the acting user and passes it to the application service for audit.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final AccountService accountService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
    String actor = userContextProvider.currentUser().username();
    AccountView view =
        accountService.register(
            request.legalType(),
            request.documentNumber(),
            request.displayName(),
            request.cadastur(),
            request.iata(),
            actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(view));
  }

  @GetMapping("/{id}")
  public AccountResponse get(@PathVariable UUID id) {
    return AccountResponse.from(accountService.getById(id));
  }

  @GetMapping
  public PageResponse<AccountResponse> list(
      @RequestParam(required = false) AccountStatus status,
      @RequestParam(required = false) String document,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<AccountResponse> result =
        accountService.list(status, document, pageable).map(AccountResponse::from);
    return PageResponse.from(result);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
