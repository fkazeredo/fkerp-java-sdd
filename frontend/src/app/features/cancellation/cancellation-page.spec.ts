import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { CancellationPage } from './cancellation-page';
import { CancellationPolicyView } from './cancellation.models';
import { CancellationService } from './cancellation.service';

const POLICY: CancellationPolicyView = {
  scopeRef: 'HOTEL-XYZ',
  type: 'ALL_SALES_FINAL',
  windows: [{ hoursBefore: 48, penaltyPct: 0.5 }],
  refundable: false,
  costBearer: 'ACME',
  merchantOfRecord: true,
  noShowFee: { amount: 30, currency: 'BRL' },
  waivedIfFlightCancelled: true,
};

function configure(service: Partial<CancellationService>): void {
  TestBed.configureTestingModule({
    imports: [CancellationPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: CancellationService, useValue: service },
    ],
  });
}

describe('CancellationPage', () => {
  it('starts idle before any lookup', () => {
    configure({});
    const fixture = TestBed.createComponent(CancellationPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('idle');
  });

  it('looks up a policy and seeds the edit form (loading → success)', () => {
    configure({ get: () => of(POLICY) });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'HOTEL-XYZ';
    page.lookup();

    expect(page.state()).toBe('success');
    expect(page.policy()?.type).toBe('ALL_SALES_FINAL');
    expect(page.editRefundable).toBe(false);
    expect(page.windows().length).toBe(1);
    expect(page.isDirty()).toBe(false);
  });

  it('shows the error state when the lookup fails', () => {
    configure({
      get: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'missing';
    page.lookup();

    expect(page.state()).toBe('error');
    expect(page.errorCode()).toBe('error.internal');
  });

  it('renders the permission state on a 403 (access.denied)', () => {
    configure({
      get: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'x';
    page.lookup();

    expect(page.errorCode()).toBe('access.denied');
  });

  it('adds and removes penalty windows and marks the form dirty', () => {
    configure({ get: () => of(POLICY) });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'HOTEL-XYZ';
    page.lookup();

    page.newWindowHours = 24;
    page.newWindowPct = 0.25;
    page.addWindow();
    expect(page.windows().length).toBe(2);
    expect(page.isDirty()).toBe(true);

    page.removeWindow(0);
    expect(page.windows().length).toBe(1);
  });

  it('saves the policy and clears the dirty flag', () => {
    const put = vi.fn(() => of({ ...POLICY, refundable: true }));
    configure({ get: () => of(POLICY), put });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'HOTEL-XYZ';
    page.lookup();
    page.markDirty();
    expect(page.isDirty()).toBe(true);

    page.save();

    expect(put).toHaveBeenCalled();
    expect(page.policy()?.refundable).toBe(true);
    expect(page.isDirty()).toBe(false);
  });

  it('surfaces a malformed-window save error by its stable code', () => {
    configure({
      get: () => of(POLICY),
      put: () =>
        throwError(() => ({ code: 'cancellation.policy.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CancellationPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.scopeRef = 'HOTEL-XYZ';
    page.lookup();
    page.save();

    expect(page.saveError()).toBe('cancellation.policy.invalid');
  });

  it('maps cancellation-type severities', () => {
    configure({});
    const page = TestBed.createComponent(CancellationPage).componentInstance;

    expect(page.typeSeverity('ALL_SALES_FINAL')).toBe('danger');
    expect(page.typeSeverity('CUSTOM')).toBe('info');
    expect(page.typeSeverity('STANDARD')).toBe('secondary');
  });
});
