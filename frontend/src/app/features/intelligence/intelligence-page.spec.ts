import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { PageResponse } from '../../core/models/api.models';
import { IntelligencePage } from './intelligence-page';
import { InsightView } from './intelligence.models';
import { IntelligenceService } from './intelligence.service';

const INSIGHT: InsightView = {
  id: 'i1',
  type: 'PROMO_FX_ADVISOR',
  subjectKind: 'AGENCY',
  subjectRef: 'acc-1',
  evidence: {
    accruedSubsidy: { amount: 100, currency: 'BRL' },
    realizedGap: { amount: 20, currency: 'BRL' },
    volumeAttracted: 5,
    sources: ['BookingConfirmed'],
  },
  recommendation: {
    verdict: 'QUEIMA_MARGEM',
    action: 'Apertar a promoção',
    estimatedGain: null,
    estimatedRisk: { amount: 80, currency: 'BRL' },
  },
  guardrail: { description: 'Subsídio acima do teto', thresholdCrossed: { amount: 90, currency: 'BRL' } },
  status: 'NEW',
  generatedAt: '2026-06-01T00:00:00Z',
  decidedBy: null,
  decidedAt: null,
};

function page(content: InsightView[]): PageResponse<InsightView> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function configure(service: Partial<IntelligenceService>): void {
  TestBed.configureTestingModule({
    imports: [IntelligencePage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: IntelligenceService, useValue: service },
    ],
  });
}

describe('IntelligencePage', () => {
  it('loads the insight list (loading → success)', () => {
    configure({ list: () => of(page([INSIGHT])) });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('success');
    expect(fixture.componentInstance.insights().length).toBe(1);
  });

  it('shows the empty state when there are no insights', () => {
    configure({ list: () => of(page([])) });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('empty');
  });

  it('shows the error state when the list fails', () => {
    configure({
      list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('error.internal');
  });

  it('renders the permission state on a 403 (access.denied)', () => {
    configure({
      list: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('records a decision on the selected insight', () => {
    const decide = vi.fn(() => of({ ...INSIGHT, status: 'ACCEPTED' as const }));
    configure({ list: () => of(page([INSIGHT])), decide });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.select(INSIGHT);
    component.decision = 'ACCEPTED';
    component.decide();

    expect(decide).toHaveBeenCalledWith('i1', { decision: 'ACCEPTED', note: null });
    expect(component.selected()?.status).toBe('ACCEPTED');
  });

  it('surfaces a decision error by its code', () => {
    configure({
      list: () => of(page([INSIGHT])),
      decide: () =>
        throwError(() => ({ code: 'intelligence.decision.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.select(INSIGHT);
    component.decide();

    expect(component.decisionError()).toBe('intelligence.decision.invalid');
  });

  it('maps status and verdict severities', () => {
    configure({ list: () => of(page([])) });
    const component = TestBed.createComponent(IntelligencePage).componentInstance;

    expect(component.statusSeverity('ACCEPTED')).toBe('success');
    expect(component.statusSeverity('REJECTED')).toBe('danger');
    expect(component.verdictSeverity('CONVERTE')).toBe('success');
    expect(component.verdictSeverity('QUEIMA_MARGEM')).toBe('warn');
  });
});

describe('IntelligencePage — additional method coverage', () => {
  it('selects an insight and clears the decision error', () => {
    configure({ list: () => of(page([INSIGHT])) });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.decisionError.set('x');
    component.select(INSIGHT);

    expect(component.selected()?.id).toBe('i1');
    expect(component.decisionError()).toBeNull();
  });

  it('does nothing on decide when no insight is selected', () => {
    const decide = vi.fn(() => of(INSIGHT));
    configure({ list: () => of(page([INSIGHT])), decide });
    const fixture = TestBed.createComponent(IntelligencePage);
    fixture.detectChanges();

    fixture.componentInstance.decide();

    expect(decide).not.toHaveBeenCalled();
  });
});
