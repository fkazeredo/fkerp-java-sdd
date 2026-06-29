package com.fksoft.application.api;

import com.fksoft.application.api.dto.CancelBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.domain.booking.BookingService;
import com.fksoft.domain.booking.BookingStatus;
import com.fksoft.domain.booking.BookingView;
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
 * REST endpoints for bookings (SPEC-0006): create from a quote and drive the lifecycle through
 * domain-action endpoints. The delivery layer resolves the acting user for audit.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final BookingService bookingService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<BookingView> create(@Valid @RequestBody CreateBookingRequest request) {
    BookingView view =
        bookingService.create(
            request.quoteId(), request.locator().origin(), request.locator().code(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/{id}/pending")
  public BookingView pending(@PathVariable UUID id) {
    return bookingService.transition(id, BookingStatus.PENDING, null, actor());
  }

  @PostMapping("/{id}/confirm")
  public BookingView confirm(@PathVariable UUID id) {
    return bookingService.transition(id, BookingStatus.CONFIRMED, null, actor());
  }

  @PostMapping("/{id}/cancel")
  public BookingView cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelBookingRequest request) {
    return bookingService.transition(id, BookingStatus.CANCELLED, request.reason(), actor());
  }

  @PostMapping("/{id}/no-show")
  public BookingView noShow(@PathVariable UUID id) {
    return bookingService.transition(id, BookingStatus.NO_SHOW, null, actor());
  }

  @PostMapping("/{id}/complete")
  public BookingView complete(@PathVariable UUID id) {
    return bookingService.transition(id, BookingStatus.COMPLETED, null, actor());
  }

  @PostMapping("/{id}/change")
  public BookingView change(@PathVariable UUID id) {
    return bookingService.transition(id, BookingStatus.CHANGED, null, actor());
  }

  @GetMapping("/{id}")
  public BookingView get(@PathVariable UUID id) {
    return bookingService.getById(id);
  }

  @GetMapping
  public PageResponse<BookingView> list(
      @RequestParam(required = false) BookingStatus status,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<BookingView> result = bookingService.list(status, accountId, pageable);
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
