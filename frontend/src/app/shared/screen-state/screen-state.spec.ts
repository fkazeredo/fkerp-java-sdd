import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { ScreenState, ScreenStateKind } from './screen-state';

@Component({
  imports: [ScreenState],
  template: `
    <app-screen-state [state]="state()" [errorCode]="code()" emptyKey="accounts.empty">
      <p data-testid="content">content</p>
    </app-screen-state>
  `,
})
class Host {
  readonly state = signal<ScreenStateKind>('loading');
  readonly code = signal<string | null>(null);
}

function create(): { fixture: ReturnType<typeof TestBed.createComponent<Host>>; host: Host } {
  TestBed.configureTestingModule({
    imports: [Host],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
    ],
  });
  const fixture = TestBed.createComponent(Host);
  return { fixture, host: fixture.componentInstance };
}

describe('ScreenState (AC9/BR8)', () => {
  it('shows the loading state', () => {
    const { fixture } = create();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="state-loading"]')).toBeTruthy();
  });

  it('shows the empty state', () => {
    const { fixture, host } = create();
    host.state.set('empty');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="state-empty"]')).toBeTruthy();
  });

  it('shows a generic error with retry', () => {
    const { fixture, host } = create();
    host.state.set('error');
    host.code.set('error.internal');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="state-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="state-retry"]')).toBeTruthy();
  });

  it('shows a permission state for a 403-style code (AC9)', () => {
    const { fixture, host } = create();
    host.state.set('error');
    host.code.set('access.denied');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="state-permission"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="state-error"]')).toBeNull();
  });

  it('projects the children on success', () => {
    const { fixture, host } = create();
    host.state.set('success');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="content"]')).toBeTruthy();
  });
});
