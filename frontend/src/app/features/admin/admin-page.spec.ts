import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { AdminContractView, AdminExpenseView, AdminSupplierView } from './admin.models';
import { AdminPage } from './admin-page';
import { AdminService } from './admin.service';

const SUPPLIER: AdminSupplierView = {
  id: 's1',
  type: 'UTILITY',
  identifier: 'S-1',
  displayName: 'Acme Energy',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
};

const EXPENSE: AdminExpenseView = {
  id: 'x1',
  supplierId: 's1',
  period: '2026-01',
  amount: { amount: 100, currency: 'BRL' },
  kind: 'UTILITY',
  financeEntryId: 'f1',
  requiredDocuments: ['INVOICE'],
  createdAt: '2026-01-01T00:00:00Z',
};

function configure(service: Partial<AdminService>): void {
  TestBed.configureTestingModule({
    imports: [AdminPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AdminService, useValue: service },
    ],
  });
}

describe('AdminPage', () => {
  it('loads the supplier list (loading → success)', () => {
    configure({ listSuppliers: () => of([SUPPLIER]) });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.suppliersState()).toBe('success');
    expect(fixture.componentInstance.suppliers().length).toBe(1);
  });

  it('shows the empty state when there are no suppliers', () => {
    configure({ listSuppliers: () => of([]) });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.suppliersState()).toBe('empty');
  });

  it('renders the permission state on a 403 supplier list', () => {
    configure({
      listSuppliers: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('registers a supplier and reloads the list', () => {
    const registerSupplier = vi.fn(() => of(SUPPLIER));
    const listSuppliers = vi.fn(() => of([SUPPLIER]));
    configure({ listSuppliers, registerSupplier });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newIdentifier = 'S-1';
    component.newDisplayName = 'Acme Energy';
    expect(component.isDirty()).toBe(true);
    component.registerSupplier();

    expect(registerSupplier).toHaveBeenCalled();
    expect(listSuppliers).toHaveBeenCalledTimes(2);
  });

  it('surfaces a register error by its code (403 write)', () => {
    configure({
      listSuppliers: () => of([]),
      registerSupplier: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newIdentifier = 'S-1';
    component.newDisplayName = 'Acme';
    component.registerSupplier();

    expect(component.registerError()).toBe('access.denied');
  });

  it('loads a supplier contracts (empty)', () => {
    configure({ listSuppliers: () => of([]), contracts: () => of<AdminContractView[]>([]) });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.contractSupplierId = 's1';
    component.loadContracts();

    expect(component.contractsListState()).toBe('empty');
  });

  it('registers a recurring expense and shows the finance entry', () => {
    const registerExpense = vi.fn(() => of(EXPENSE));
    configure({ listSuppliers: () => of([]), registerExpense });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.expenseSupplierId = 's1';
    component.expensePeriod = '2026-01';
    component.expenseAmount = 100;
    component.registerExpense();

    expect(registerExpense).toHaveBeenCalled();
    expect(component.expense()?.financeEntryId).toBe('f1');
  });

  it('runs the contract-expiry sweep', () => {
    const flagExpiring = vi.fn(() => of({ flagged: 4 }));
    configure({ listSuppliers: () => of([]), flagExpiring });
    const fixture = TestBed.createComponent(AdminPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.runSweep();

    expect(component.sweepResult()).toBe(4);
  });

  it('maps supplier status severities', () => {
    configure({ listSuppliers: () => of([]) });
    const component = TestBed.createComponent(AdminPage).componentInstance;

    expect(component.statusSeverity('ACTIVE')).toBe('success');
    expect(component.statusSeverity('INACTIVE')).toBe('secondary');
  });
});
