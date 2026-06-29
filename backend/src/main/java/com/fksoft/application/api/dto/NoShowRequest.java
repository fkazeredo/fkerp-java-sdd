package com.fksoft.application.api.dto;

/**
 * Request body for {@code POST /api/bookings/{id}/no-show} (SPEC-0010 BR6): optional proof that the
 * customer's flight was cancelled. When the booking's frozen no-show policy has {@code
 * waivedIfFlightCancelled = true} and proof is provided, the fee is waived; otherwise it is
 * charged. The proof's compliance verification is out of scope here (DL-0023) — this only flags
 * that proof was provided. The body is optional; a missing body means no proof.
 *
 * @param flightCancelledProof whether proof of a cancelled flight is provided (defaults to false)
 */
public record NoShowRequest(Boolean flightCancelledProof) {

  /** Whether proof was provided ({@code false} when the field/body is absent). */
  public boolean hasProof() {
    return Boolean.TRUE.equals(flightCancelledProof);
  }
}
