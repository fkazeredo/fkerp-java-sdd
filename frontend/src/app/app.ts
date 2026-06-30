import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root host. The authenticated screens render inside the {@link Shell} layout route; the login
 * screen renders standalone (no shell). This component is just the top-level router outlet.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
