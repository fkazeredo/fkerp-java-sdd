import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { AssetView } from './assets.models';
import { AssetsPage } from './assets-page';
import { AssetsService } from './assets.service';

const ASSET: AssetView = {
  id: 'a1',
  type: 'EQUIPMENT',
  identifier: 'NB-1',
  status: 'ACTIVE',
  acquisitionDate: '2026-01-01',
  acquisitionCost: { amount: 1000, currency: 'BRL' },
  expiresAt: null,
  supplierRef: null,
  documentId: null,
  financeEntryId: null,
  retiredAt: null,
  retiredBy: null,
  retirementReason: null,
  createdAt: '2026-01-01T00:00:00Z',
};

function configure(service: Partial<AssetsService>): void {
  TestBed.configureTestingModule({
    imports: [AssetsPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AssetsService, useValue: service },
    ],
  });
}

describe('AssetsPage', () => {
  it('loads the asset list (loading → success)', () => {
    configure({ list: () => of([ASSET]) });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.assetsState()).toBe('success');
    expect(fixture.componentInstance.assets().length).toBe(1);
  });

  it('shows the empty state when there are no assets', () => {
    configure({ list: () => of([]) });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.assetsState()).toBe('empty');
  });

  it('shows the error state when the asset list fails', () => {
    configure({ list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })) });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.assetsState()).toBe('error');
  });

  it('renders the permission state on a 403 asset list', () => {
    configure({ list: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })) });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('registers an asset and reloads the list', () => {
    const register = vi.fn(() => of(ASSET));
    const list = vi.fn(() => of([ASSET]));
    configure({ list, register });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newIdentifier = 'NB-1';
    component.newAcquisitionDate = '2026-01-01';
    component.newCostAmount = 1000;
    expect(component.isDirty()).toBe(true);
    component.registerAsset();

    expect(register).toHaveBeenCalled();
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('retires an asset and reloads the list', () => {
    const retire = vi.fn(() => of({ ...ASSET, status: 'RETIRED' as const }));
    const list = vi.fn(() => of([ASSET]));
    configure({ list, retire });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.retireReason = 'broken';
    component.retireAsset(ASSET);

    expect(retire).toHaveBeenCalledWith('a1', { reason: 'broken' });
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('runs the license-expiry sweep and shows the count', () => {
    const flagExpiring = vi.fn(() => of({ flagged: 2 }));
    configure({ list: () => of([]), flagExpiring });
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.runSweep();

    expect(flagExpiring).toHaveBeenCalled();
    expect(component.sweepResult()).toBe(2);
  });

  it('maps asset status severities', () => {
    configure({ list: () => of([]) });
    const component = TestBed.createComponent(AssetsPage).componentInstance;

    expect(component.statusSeverity('ACTIVE')).toBe('success');
    expect(component.statusSeverity('RETIRED')).toBe('secondary');
  });
});
