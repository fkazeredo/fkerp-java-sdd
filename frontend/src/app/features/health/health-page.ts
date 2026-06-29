import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiError } from '../../core/http/api-error';
import { SystemHealth } from './health.models';
import { HealthService } from './health.service';

type ViewState = 'loading' | 'success' | 'error';

/**
 * Walking-skeleton screen (SPEC-0001): calls `GET /api/system/health` and renders loading, success
 * and error states, proving the stack end to end from the browser. Labels go through ngx-translate.
 */
@Component({
  selector: 'app-health-page',
  imports: [TranslatePipe],
  templateUrl: './health-page.html',
  styleUrl: './health-page.scss',
})
export class HealthPage implements OnInit {
  private readonly healthService = inject(HealthService);

  readonly state = signal<ViewState>('loading');
  readonly health = signal<SystemHealth | null>(null);
  readonly errorCode = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  /** (Re)loads the health status; exposed so the error state's retry button can call it. */
  load(): void {
    this.state.set('loading');
    this.healthService.getHealth().subscribe({
      next: (health) => {
        this.health.set(health);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }
}
