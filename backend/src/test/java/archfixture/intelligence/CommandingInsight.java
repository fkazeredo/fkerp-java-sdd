package archfixture.intelligence;

import com.fksoft.domain.booking.BookingService;

/**
 * Test fixture that deliberately violates the "advises, never commands" rule (SPEC-0013 BR2) by
 * having an intelligence-side type depend on another module's command facade ({@code
 * BookingService}). {@code ArchitectureRulesHaveTeethTest} asserts the production rule fails
 * against this, proving the gate has teeth. It lives outside the real {@code
 * com.fksoft.domain.intelligence} package and outside the production import, so it never affects
 * the real verification — the teeth test re-points the rule's source package at this fixture
 * explicitly.
 */
public class CommandingInsight {

  private final BookingService bookingService;

  public CommandingInsight(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  public Object commands() {
    // Touching the command facade is exactly what the rule forbids.
    return bookingService;
  }
}
