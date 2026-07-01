import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { canDeactivateGuard } from './core/guards/can-deactivate.guard';

/**
 * App routes. The authenticated screens render inside the {@link Shell} layout route; the login
 * screen renders standalone. Feature screens are lazy-loaded so the PrimeNG-heavy pages are split
 * into their own chunks and the initial bundle stays small (SPEC-0026 AC1).
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
        canActivate: [authGuard],
      },
      {
        path: 'accounts',
        loadComponent: () => import('./features/accounts/accounts-page').then((m) => m.AccountsPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'exchange',
        loadComponent: () => import('./features/exchange/exchange-page').then((m) => m.ExchangePage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'exchange-desk',
        loadComponent: () =>
          import('./features/exchange/exchange-desk-page').then((m) => m.ExchangeDeskPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'quotes',
        loadComponent: () => import('./features/quoting/quoting-page').then((m) => m.QuotingPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'bookings',
        loadComponent: () => import('./features/booking/booking-page').then((m) => m.BookingPage),
        canActivate: [authGuard],
      },
      {
        path: 'reconciliation',
        loadComponent: () =>
          import('./features/reconciliation/reconciliation-page').then((m) => m.ReconciliationPage),
        canActivate: [authGuard],
      },
      {
        path: 'finance',
        loadComponent: () => import('./features/finance/finance-page').then((m) => m.FinancePage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'billing',
        loadComponent: () => import('./features/billing/billing-page').then((m) => m.BillingPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'payouts',
        loadComponent: () => import('./features/payout/payout-page').then((m) => m.PayoutPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'compliance',
        loadComponent: () =>
          import('./features/compliance/compliance-page').then((m) => m.CompliancePage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'aftersales',
        loadComponent: () =>
          import('./features/aftersales/aftersales-page').then((m) => m.AfterSalesPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'sourcing',
        loadComponent: () => import('./features/sourcing/sourcing-page').then((m) => m.SourcingPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'cancellation',
        loadComponent: () =>
          import('./features/cancellation/cancellation-page').then((m) => m.CancellationPage),
        canActivate: [authGuard],
        canDeactivate: [canDeactivateGuard],
      },
      {
        path: 'health',
        loadComponent: () => import('./features/health/health-page').then((m) => m.HealthPage),
      },
    ],
  },
];
