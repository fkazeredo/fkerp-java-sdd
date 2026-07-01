import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { PageResponse } from '../../core/models/api.models';
import { AccessAuditView, RoleView } from './identity.models';
import { IdentityPage } from './identity-page';
import { IdentityService } from './identity.service';

const ROLE: RoleView = {
  name: 'ROLE_IT',
  description: 'IT operator',
  permissions: ['platform:job:trigger'],
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function base(overrides: Partial<IdentityService> = {}): Partial<IdentityService> {
  return {
    roles: () => of([ROLE]),
    accessAudit: () => of(page<AccessAuditView>([])),
    ...overrides,
  };
}

function configure(service: Partial<IdentityService>): void {
  TestBed.configureTestingModule({
    imports: [IdentityPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: IdentityService, useValue: service },
    ],
  });
}

describe('IdentityPage', () => {
  it('loads the role catalogue (loading → success)', () => {
    configure(base());
    const fixture = TestBed.createComponent(IdentityPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.rolesListState()).toBe('success');
    expect(fixture.componentInstance.roles().length).toBe(1);
  });

  it('shows the empty audit state', () => {
    configure(base());
    const fixture = TestBed.createComponent(IdentityPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.auditListState()).toBe('empty');
  });

  it('renders the permission state on a 403 role catalogue', () => {
    configure(
      base({ roles: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })) }),
    );
    const fixture = TestBed.createComponent(IdentityPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.rolesError()).toBe('access.denied');
  });

  it('shows the error state when the audit trail fails', () => {
    configure(
      base({
        accessAudit: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(IdentityPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.auditListState()).toBe('error');
  });

  it('maps access-audit type severities', () => {
    configure(base());
    const component = TestBed.createComponent(IdentityPage).componentInstance;

    expect(component.auditSeverity('AUTH_LOGIN')).toBe('success');
    expect(component.auditSeverity('ACCESS_DENIED')).toBe('danger');
    expect(component.auditSeverity('SECURITY_EVENT')).toBe('warn');
  });
});
