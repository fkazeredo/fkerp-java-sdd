import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { App } from './app';
import { InMemoryTranslateLoader } from './core/i18n/in-memory-translate.loader';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('creates the app shell with the navigation and a login link when logged out', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        provideTranslateService({
          lang: 'pt-BR',
          fallbackLang: 'pt-BR',
          loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
        }),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    // 6 feature links + the login link (logged out).
    expect(fixture.nativeElement.querySelectorAll('.nav a').length).toBe(7);
  });
});
