import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { HealthPage } from './health-page';
import { SystemHealth } from './health.models';
import { HealthService } from './health.service';

function configure(healthService: Partial<HealthService>): void {
  TestBed.configureTestingModule({
    imports: [HealthPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: HealthService, useValue: healthService },
    ],
  });
}

describe('HealthPage', () => {
  it('shows the success state when the backend reports UP', () => {
    configure({ getHealth: () => of({ status: 'UP', db: 'UP' }) });
    const fixture = TestBed.createComponent(HealthPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.health()?.status).toBe('UP');
    expect(fixture.componentInstance.health()?.db).toBe('UP');
  });

  it('shows the error state with the error code when the request fails', () => {
    configure({
      getHealth: () => throwError(() => ({ code: 'error.network', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(HealthPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('error.network');
  });

  it('starts in the loading state until the response resolves', () => {
    const pending = new Subject<SystemHealth>();
    configure({ getHealth: () => pending.asObservable() });
    const fixture = TestBed.createComponent(HealthPage);

    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('loading');

    pending.next({ status: 'UP', db: 'UP' });
    pending.complete();
    expect(fixture.componentInstance.state()).toBe('success');
  });
});
