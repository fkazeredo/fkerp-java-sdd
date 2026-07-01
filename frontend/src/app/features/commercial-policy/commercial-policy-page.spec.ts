import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { ParameterRuleResponse, ResolvedParameterResponse } from './commercial-policy.models';
import { CommercialPolicyPage } from './commercial-policy-page';
import { CommercialPolicyService } from './commercial-policy.service';

const RESOLVED: ResolvedParameterResponse = {
  key: 'MARKUP_PCT',
  value: '0.12',
  type: 'PERCENT',
  provenance: {
    layer: 'DIRECTIVE',
    ruleId: 'r1',
    definedBy: 'director',
    definedAt: '2026-06-01T00:00:00Z',
    validUntil: null,
  },
};

const RULE: ParameterRuleResponse = {
  id: 'r1',
  key: 'MARKUP_PCT',
  layer: 'POLICY',
  accountId: null,
  productRef: null,
  channel: null,
  value: '0.12',
  type: 'PERCENT',
  validFrom: '2026-01-01',
  validUntil: null,
  definedBy: 'policy',
  justification: null,
  createdAt: '2026-06-01T00:00:00Z',
};

function configure(service: Partial<CommercialPolicyService>): void {
  TestBed.configureTestingModule({
    imports: [CommercialPolicyPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: CommercialPolicyService, useValue: service },
    ],
  });
}

describe('CommercialPolicyPage', () => {
  it('loads the rules audit list (loading → success)', () => {
    configure({ listRules: () => of([RULE]) });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.rulesState()).toBe('success');
    expect(fixture.componentInstance.rules().length).toBe(1);
  });

  it('shows the empty state when there are no rules', () => {
    configure({ listRules: () => of([]) });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.rulesState()).toBe('empty');
  });

  it('shows the error state when the rules list fails', () => {
    configure({
      listRules: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.rulesState()).toBe('error');
  });

  it('resolves a parameter and exposes the winning provenance', () => {
    configure({ listRules: () => of([]), resolve: () => of(RESOLVED) });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.resolveKey = 'MARKUP_PCT';
    component.resolve();

    expect(component.resolveState()).toBe('success');
    expect(component.resolved()?.provenance.layer).toBe('DIRECTIVE');
  });

  it('renders the permission state on a 403 when issuing a directive without the role', () => {
    configure({
      listRules: () => of([]),
      issueDirective: () =>
        throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.directiveKey = 'MARKUP_PCT';
    component.directiveValue = '0.2';
    component.directiveJustification = 'campanha';
    component.issueDirective();

    expect(component.directiveError()).toBe('access.denied');
  });

  it('defines a rule and reloads the list', () => {
    const defineRule = vi.fn(() => of(RULE));
    const listRules = vi.fn(() => of([RULE]));
    configure({ listRules, defineRule });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.ruleKey = 'MARKUP_PCT';
    component.ruleValue = '0.12';
    expect(component.isDirty()).toBe(true);
    component.defineRule();

    expect(defineRule).toHaveBeenCalled();
    expect(listRules).toHaveBeenCalledTimes(2);
    expect(component.isDirty()).toBe(false);
  });

  it('maps layer severities with the directive at the top', () => {
    configure({ listRules: () => of([]) });
    const component = TestBed.createComponent(CommercialPolicyPage).componentInstance;

    expect(component.layerSeverity('DIRECTIVE')).toBe('danger');
    expect(component.layerSeverity('PROMOTION')).toBe('warn');
    expect(component.layerSeverity('SYSTEM_DEFAULT')).toBe('secondary');
  });
});

describe('CommercialPolicyPage — additional method coverage', () => {
  it('surfaces a resolve error by its code', () => {
    configure({
      listRules: () => of([]),
      resolve: () =>
        throwError(() => ({ code: 'policy.parameter.unknown', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.resolveKey = 'UNKNOWN';
    component.resolve();

    expect(component.resolveState()).toBe('error');
    expect(component.resolveError()).toBe('policy.parameter.unknown');
  });

  it('issues a directive and reloads the rules', () => {
    const issueDirective = vi.fn(() => of(RULE));
    const listRules = vi.fn(() => of([RULE]));
    configure({ listRules, issueDirective });
    const fixture = TestBed.createComponent(CommercialPolicyPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.directiveKey = 'MARKUP_PCT';
    component.directiveValue = '0.2';
    component.directiveJustification = 'campanha';
    component.issueDirective();

    expect(issueDirective).toHaveBeenCalled();
    expect(listRules).toHaveBeenCalledTimes(2);
  });
});
