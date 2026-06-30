package archfixture.platform;

import com.fksoft.domain.booking.BookingService;

/**
 * Test fixture that deliberately violates the "Platform orchestrates, never owns domain rules" rule
 * (SPEC-0023 BR6) by having a platform-side type depend on another module's command facade ({@code
 * BookingService}). {@code ArchitectureRulesHaveTeethTest} asserts the production rule fails
 * against this, proving the gate has teeth. It lives outside the real {@code
 * com.fksoft.domain.platform} package and outside the production import, so it never affects the
 * real verification — the teeth test re-points the rule's source package at this fixture
 * explicitly.
 */
public class CommandingPlatform {

  private final BookingService bookingService;

  public CommandingPlatform(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  public Object commands() {
    // Touching the command facade is exactly what the rule forbids for Platform.
    return bookingService;
  }
}
