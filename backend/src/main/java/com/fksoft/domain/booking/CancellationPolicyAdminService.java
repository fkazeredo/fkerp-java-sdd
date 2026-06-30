package com.fksoft.domain.booking;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that administers the cancellation/no-show policy source per product/supplier
 * scope (SPEC-0010 API: {@code GET/PUT /api/products/{ref}/cancellation-policy}). This is the
 * source of the snapshot a booking freezes at confirmation (BR1); the freeze itself lives in {@link
 * BookingService}. Reading a scope with no administered policy returns the safe default (an
 * affiliate STANDARD policy with no windows and no no-show fee).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationPolicyAdminService {

  private final CancellationPolicySourceRepository repository;
  private final Clock clock;

  /**
   * The administered policy for a scope, or the safe default when none was set.
   *
   * @param scopeRef the product/supplier scope reference
   * @return the policy view (default = affiliate STANDARD, no windows, no no-show fee)
   */
  @Transactional(readOnly = true)
  public CancellationPolicyView get(String scopeRef) {
    return repository
        .findByScopeRef(scopeRef)
        .map(CancellationPolicySource::toView)
        .orElseGet(() -> defaultView(scopeRef));
  }

  /**
   * Upserts the policy for a scope (idempotent by {@code scopeRef}).
   *
   * @param scopeRef the product/supplier scope reference (non-empty)
   * @param policy the cancellation policy
   * @param noShow the no-show policy
   * @param actor who administers it (audit)
   * @return the stored policy view
   * @throws CancellationPolicyInvalidException when the scope reference is blank
   */
  @Transactional
  public CancellationPolicyView put(
      String scopeRef, CancellationPolicy policy, NoShowPolicy noShow, String actor) {
    if (scopeRef == null || scopeRef.isBlank()) {
      throw new CancellationPolicyInvalidException();
    }
    String ref = scopeRef.trim();
    CancellationPolicySource source =
        repository
            .findByScopeRef(ref)
            .map(
                existing -> {
                  existing.apply(policy, noShow, clock.instant(), actor);
                  return existing;
                })
            .orElseGet(
                () -> CancellationPolicySource.create(ref, policy, noShow, clock.instant(), actor));
    repository.save(source);
    log.info(
        "CancellationPolicyAdministered scopeRef={} type={} merchantOfRecord={} performedBy={}",
        ref,
        policy.type(),
        policy.merchantOfRecord(),
        actor);
    return source.toView();
  }

  private static CancellationPolicyView defaultView(String scopeRef) {
    CancellationPolicy policy = CancellationPolicy.standardNoWindows();
    return new CancellationPolicyView(
        scopeRef,
        policy.type(),
        List.of(),
        policy.refundable(),
        policy.costBearer(),
        policy.merchantOfRecord(),
        null,
        false);
  }
}
