import { Routes } from '@angular/router';
import { AccountsPage } from './features/accounts/accounts-page';
import { BookingPage } from './features/booking/booking-page';
import { ExchangePage } from './features/exchange/exchange-page';
import { HealthPage } from './features/health/health-page';
import { QuotingPage } from './features/quoting/quoting-page';
import { ReconciliationPage } from './features/reconciliation/reconciliation-page';

export const routes: Routes = [
  { path: '', redirectTo: 'accounts', pathMatch: 'full' },
  { path: 'accounts', component: AccountsPage },
  { path: 'exchange', component: ExchangePage },
  { path: 'quotes', component: QuotingPage },
  { path: 'bookings', component: BookingPage },
  { path: 'reconciliation', component: ReconciliationPage },
  { path: 'health', component: HealthPage },
];
