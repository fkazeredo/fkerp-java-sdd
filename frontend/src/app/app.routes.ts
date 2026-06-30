import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { AccountsPage } from './features/accounts/accounts-page';
import { BookingPage } from './features/booking/booking-page';
import { ExchangePage } from './features/exchange/exchange-page';
import { HealthPage } from './features/health/health-page';
import { LoginPage } from './features/login/login-page';
import { QuotingPage } from './features/quoting/quoting-page';
import { ReconciliationPage } from './features/reconciliation/reconciliation-page';

export const routes: Routes = [
  { path: '', redirectTo: 'accounts', pathMatch: 'full' },
  { path: 'login', component: LoginPage },
  { path: 'accounts', component: AccountsPage, canActivate: [authGuard] },
  { path: 'exchange', component: ExchangePage, canActivate: [authGuard] },
  { path: 'quotes', component: QuotingPage, canActivate: [authGuard] },
  { path: 'bookings', component: BookingPage, canActivate: [authGuard] },
  { path: 'reconciliation', component: ReconciliationPage, canActivate: [authGuard] },
  { path: 'health', component: HealthPage },
];
