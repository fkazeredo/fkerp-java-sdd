import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/http/api-error';
import { Money, formatMoney } from '../../core/models/api.models';
import { ScreenState } from '../../shared/screen-state/screen-state';
import {
  AccountsKpi,
  BookingsKpi,
  DashboardService,
  ExchangeKpi,
  ReconciliationKpi,
} from './dashboard.service';

/** A loadable KPI slot with its own state, value and error code. */
class KpiSlot<T> {
  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly value = signal<T | null>(null);
  readonly errorCode = signal<string | null>(null);

  load(source: Observable<T>): void {
    this.state.set('loading');
    source.subscribe({
      next: (v) => {
        this.value.set(v);
        this.state.set('success');
      },
      error: (e: ApiError) => {
        this.errorCode.set(e?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }
}

/**
 * Dashboard with KPIs (SPEC-0026 BR10, DL-0094). Each card loads independently from the existing
 * feature endpoints (no new backend) and renders its own loading/empty/error/permission state; the
 * cards link to the related screen. This is the authenticated landing route.
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [TranslatePipe, RouterLink, CardModule, ScreenState],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage implements OnInit {
  private readonly dashboard = inject(DashboardService);

  readonly accounts = new KpiSlot<AccountsKpi>();
  readonly bookings = new KpiSlot<BookingsKpi>();
  readonly reconciliation = new KpiSlot<ReconciliationKpi>();
  readonly exchange = new KpiSlot<ExchangeKpi>();

  readonly format = (money: Money | null): string => formatMoney(money);

  ngOnInit(): void {
    this.reload();
  }

  /** (Re)loads every KPI. */
  reload(): void {
    this.accounts.load(this.dashboard.accountsKpi());
    this.bookings.load(this.dashboard.bookingsKpi());
    this.reconciliation.load(this.dashboard.reconciliationKpi());
    this.exchange.load(this.dashboard.exchangeKpi());
  }
}
