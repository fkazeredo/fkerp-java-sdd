/** A primary navigation entry for the SaaS shell (SPEC-0026 BR2). */
export interface NavItem {
  /** Router path (without leading slash). */
  readonly path: string;
  /** i18n key for the visible label. */
  readonly labelKey: string;
  /** PrimeIcons class (e.g. `pi pi-home`). */
  readonly icon: string;
  /** Optional roles that may see this item; when omitted, every authenticated user sees it. The
   *  backend remains the authority — this only hides menu noise (SPEC-0026 BR2). */
  readonly roles?: readonly string[];
}

/** The workflow-oriented primary navigation of the ERP shell. */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'dashboard', labelKey: 'nav.dashboard', icon: 'pi pi-th-large' },
  { path: 'accounts', labelKey: 'nav.accounts', icon: 'pi pi-users' },
  { path: 'exchange', labelKey: 'nav.exchange', icon: 'pi pi-dollar' },
  { path: 'quotes', labelKey: 'nav.quotes', icon: 'pi pi-calculator' },
  { path: 'bookings', labelKey: 'nav.bookings', icon: 'pi pi-ticket' },
  { path: 'reconciliation', labelKey: 'nav.reconciliation', icon: 'pi pi-sync' },
  {
    path: 'finance',
    labelKey: 'nav.finance',
    icon: 'pi pi-book',
    roles: ['ROLE_FINANCE'],
  },
  {
    path: 'billing',
    labelKey: 'nav.billing',
    icon: 'pi pi-file-edit',
    roles: ['ROLE_FINANCE'],
  },
  {
    path: 'payouts',
    labelKey: 'nav.payouts',
    icon: 'pi pi-send',
    roles: ['ROLE_FINANCE'],
  },
  { path: 'compliance', labelKey: 'nav.compliance', icon: 'pi pi-shield' },
  { path: 'health', labelKey: 'nav.health', icon: 'pi pi-heart' },
];
