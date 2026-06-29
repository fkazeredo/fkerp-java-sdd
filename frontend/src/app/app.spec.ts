import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { App } from './app';
import { InMemoryTranslateLoader } from './core/i18n/in-memory-translate.loader';

describe('App', () => {
  it('creates the app shell with the navigation', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
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
    expect(fixture.nativeElement.querySelectorAll('.nav a').length).toBe(6);
  });
});
