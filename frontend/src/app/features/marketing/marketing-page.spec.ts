import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { AttributionView, ConsentStateResponse, SegmentView } from './marketing.models';
import { MarketingPage } from './marketing-page';
import { MarketingService } from './marketing.service';

const STATE: ConsentStateResponse = {
  current: {
    subject: { id: 'acc-1', type: 'ACCOUNT' },
    purpose: 'NEWSLETTER',
    currentStatus: 'GRANTED',
    lastChangedAt: '2026-06-01T00:00:00Z',
  },
  history: [
    {
      id: 'c1',
      subjectId: 'acc-1',
      subjectType: 'ACCOUNT',
      purpose: 'NEWSLETTER',
      legalBasis: 'CONSENT',
      status: 'GRANTED',
      source: 'signup',
      createdAt: '2026-06-01T00:00:00Z',
    },
  ],
};

const SEGMENT: SegmentView = {
  id: 's1',
  name: 'B2B',
  criteria: { accountType: 'AGENCY' },
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-01T00:00:00Z',
};

function configure(service: Partial<MarketingService>): void {
  TestBed.configureTestingModule({
    imports: [MarketingPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: MarketingService, useValue: service },
    ],
  });
}

describe('MarketingPage', () => {
  it('starts idle (history empty) before any consent lookup', () => {
    configure({});
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.historyState()).toBe('empty');
  });

  it('looks up consent state and history (loading → success)', () => {
    configure({ consentState: () => of(STATE) });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.consentSubject = 'acc-1';
    component.lookupConsent();

    expect(component.consentState()).toBe('success');
    expect(component.historyState()).toBe('success');
    expect(component.consent()?.current.currentStatus).toBe('GRANTED');
  });

  it('renders the permission state on a 403 consent lookup', () => {
    configure({
      consentState: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.consentSubject = 'acc-1';
    component.lookupConsent();

    expect(component.consentState()).toBe('error');
    expect(component.consentError()).toBe('access.denied');
  });

  it('defines a segment and previews its reach', () => {
    configure({
      defineSegment: () => of(SEGMENT),
      previewSegment: () => of({ segmentId: 's1', reachable: 7 }),
    });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.segmentName = 'B2B';
    component.segmentCriteria = 'accountType=AGENCY';
    expect(component.isDirty()).toBe(true);
    component.defineSegment();

    expect(component.segment()?.id).toBe('s1');
    expect(component.preview()?.reachable).toBe(7);
    expect(component.campaignSegmentId).toBe('s1');
  });

  it('sends a campaign and shows the consent-filtered result', () => {
    const campaign = { id: 'cmp-1', segmentId: 's1', code: 'PROMO', contentRef: null, windowFrom: null, windowTo: null, status: 'SENT' as const, createdAt: '' };
    configure({
      createCampaign: () => of({ ...campaign, status: 'DRAFT' }),
      sendCampaign: () => of({ campaignId: 'cmp-1', targeted: 3, suppressedNoConsent: 1, queued: 2 }),
      getCampaign: () => of(campaign),
    });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.campaignSegmentId = 's1';
    component.campaignCode = 'PROMO';
    component.createCampaign();
    component.sendCampaign();

    expect(component.sendResult()?.suppressedNoConsent).toBe(1);
    expect(component.campaign()?.status).toBe('SENT');
  });

  it('runs the LGPD erasure', () => {
    configure({ erase: () => of({ subjectId: 'acc-1', anonymizedConsents: 2, suppressed: true }) });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.erasureSubject = 'acc-1';
    component.erase();

    expect(component.erasureResult()?.anonymizedConsents).toBe(2);
    expect(component.erasureResult()?.suppressed).toBe(true);
  });

  it('maps consent and campaign severities', () => {
    configure({});
    const component = TestBed.createComponent(MarketingPage).componentInstance;

    expect(component.consentSeverity('GRANTED')).toBe('success');
    expect(component.consentSeverity('REVOKED')).toBe('danger');
    expect(component.campaignSeverity('SENT')).toBe('success');
    expect(component.campaignSeverity('DRAFT')).toBe('secondary');
  });
});

describe('MarketingPage — additional method coverage', () => {
  const grantedRow = STATE.history[0];

  it('revokes a consent row and refreshes the history', () => {
    const revokeConsent = vi.fn(() => of(grantedRow));
    const consentStateFn = vi.fn(() => of(STATE));
    configure({ consentState: consentStateFn, revokeConsent });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.consentSubject = 'acc-1';
    component.revokeConsent('c1');

    expect(revokeConsent).toHaveBeenCalledWith('c1');
    expect(consentStateFn).toHaveBeenCalled();
  });

  it('grants consent and refreshes the history', () => {
    const grantConsent = vi.fn(() => of(grantedRow));
    configure({ consentState: () => of(STATE), grantConsent });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.consentSubject = 'acc-1';
    component.grantConsent();

    expect(grantConsent).toHaveBeenCalled();
  });

  it('registers an attribution and lists them', () => {
    const registerAttribution = vi.fn(() => of(null as unknown as AttributionView));
    const attribution = vi.fn(() => of([]));
    configure({ registerAttribution, attribution });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.attributionCode = 'PROMO';
    component.attributionBooking = 'bk-1';
    component.registerAttribution();

    expect(registerAttribution).toHaveBeenCalled();
    expect(component.attributionListState()).toBe('empty');
  });

  it('surfaces the erasure error by its code', () => {
    configure({
      erase: () => throwError(() => ({ code: 'marketing.subject.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(MarketingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.erasureSubject = 'acc-1';
    component.erase();

    expect(component.erasureError()).toBe('marketing.subject.invalid');
  });
});
