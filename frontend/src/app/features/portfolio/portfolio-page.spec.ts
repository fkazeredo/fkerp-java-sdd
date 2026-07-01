import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { BrandView, ContractView, GoalProgress } from './portfolio.models';
import { PortfolioPage } from './portfolio-page';
import { PortfolioService } from './portfolio.service';

const BRAND: BrandView = {
  id: 'b1',
  brandRef: 'ACME-AIR',
  displayName: 'Acme Airlines',
  status: 'ACTIVE',
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-01T00:00:00Z',
};

const PROGRESS: GoalProgress = {
  brandRef: 'ACME-AIR',
  period: '2026',
  metric: 'REVENUE',
  targetAmount: { amount: 1000, currency: 'BRL' },
  realizedAmount: { amount: 400, currency: 'BRL' },
  targetCount: null,
  realizedCount: null,
  attainmentPct: 40,
};

function configure(service: Partial<PortfolioService>): void {
  TestBed.configureTestingModule({
    imports: [PortfolioPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: PortfolioService, useValue: service },
    ],
  });
}

describe('PortfolioPage', () => {
  it('loads the brand list (loading → success)', () => {
    configure({ listBrands: () => of([BRAND]) });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.brandsState()).toBe('success');
    expect(fixture.componentInstance.brands().length).toBe(1);
  });

  it('shows the empty state when there are no brands', () => {
    configure({ listBrands: () => of([]) });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.brandsState()).toBe('empty');
  });

  it('shows the error state when the brand list fails', () => {
    configure({
      listBrands: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.brandsState()).toBe('error');
  });

  it('renders the permission state on a 403 brand list', () => {
    configure({
      listBrands: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('registers a brand and reloads the list', () => {
    const registerBrand = vi.fn(() => of(BRAND));
    const listBrands = vi.fn(() => of([BRAND]));
    configure({ listBrands, registerBrand });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newBrandRef = 'ACME-AIR';
    component.newDisplayName = 'Acme Airlines';
    expect(component.isDirty()).toBe(true);
    component.registerBrand();

    expect(registerBrand).toHaveBeenCalled();
    expect(listBrands).toHaveBeenCalledTimes(2);
    expect(component.isDirty()).toBe(false);
  });

  it('loads contracts and coverage for a brand', () => {
    configure({
      listBrands: () => of([]),
      contracts: () => of([]),
      contractCoverage: () => of({ brandRef: 'ACME-AIR', on: '2026-06-01', covered: true }),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.contractBrandRef = 'ACME-AIR';
    component.loadContracts();

    expect(component.contractsListState()).toBe('empty');
    expect(component.coverage()?.covered).toBe(true);
  });

  it('defines a goal and reads its progress (target × realized × attainment)', () => {
    configure({
      listBrands: () => of([]),
      defineGoal: () => of({ id: 'b1', brandRef: 'ACME-AIR', period: '2026', metric: 'REVENUE', targetAmount: null, targetCount: null, createdAt: '' }),
      goalProgress: () => of(PROGRESS),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.goalBrandRef = 'ACME-AIR';
    component.goalPeriod = '2026';
    component.goalMetric = 'REVENUE';
    component.goalTargetAmount = 1000;
    component.defineGoal();

    expect(component.progress()?.attainmentPct).toBe(40);
  });

  it('maps brand status severities', () => {
    configure({ listBrands: () => of([]) });
    const component = TestBed.createComponent(PortfolioPage).componentInstance;

    expect(component.statusSeverity('ACTIVE')).toBe('success');
    expect(component.statusSeverity('INACTIVE')).toBe('secondary');
  });
});

describe('PortfolioPage — additional method coverage', () => {
  it('deactivates a brand and reloads the list', () => {
    const deactivateBrand = vi.fn(() => of({ ...BRAND, status: 'INACTIVE' as const }));
    const listBrands = vi.fn(() => of([BRAND]));
    configure({ listBrands, deactivateBrand });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.deactivateBrand(BRAND);

    expect(deactivateBrand).toHaveBeenCalledWith('b1');
    expect(listBrands).toHaveBeenCalledTimes(2);
  });

  it('registers a contract and reloads the contracts', () => {
    const registerContract = vi.fn(() => of(null as unknown as ContractView));
    configure({
      listBrands: () => of([]),
      registerContract,
      contracts: () => of([]),
      contractCoverage: () => of({ brandRef: 'ACME-AIR', on: '2026-06-01', covered: false }),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.contractBrandRef = 'ACME-AIR';
    component.newContractValidFrom = '2026-01-01';
    component.registerContract();

    expect(registerContract).toHaveBeenCalled();
    expect(component.coverage()?.covered).toBe(false);
  });

  it('surfaces a goal error by its code', () => {
    configure({
      listBrands: () => of([]),
      defineGoal: () =>
        throwError(() => ({ code: 'portfolio.goal.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PortfolioPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.goalBrandRef = 'ACME-AIR';
    component.goalPeriod = '2026';
    component.goalMetric = 'VOLUME';
    component.goalTargetCount = 10;
    component.defineGoal();

    expect(component.goalError()).toBe('portfolio.goal.invalid');
  });
});
