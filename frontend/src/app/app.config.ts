import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/auth/auth.interceptor';
import { correlationIdInterceptor } from './core/http/correlation-id.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { InMemoryTranslateLoader } from './core/i18n/in-memory-translate.loader';
import { DEFAULT_LANG } from './core/i18n/translations';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([correlationIdInterceptor, authInterceptor, errorInterceptor]),
    ),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          // Dark mode is driven by the `.app-dark` class (toggled by ThemeService — DL-0091).
          darkModeSelector: '.app-dark',
          // Emit PrimeNG styles into a `primeng` CSS layer so Tailwind coexists cleanly (DL-0090).
          cssLayer: { name: 'primeng', order: 'tailwind-base, primeng, tailwind-utilities' },
        },
      },
      ripple: true,
    }),
    provideTranslateService({
      lang: DEFAULT_LANG,
      fallbackLang: DEFAULT_LANG,
      loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
    }),
    // Silent session revalidation on boot (SPEC-0026 BR7, DL-0092): if a token is stored, verify it
    // against the backend (`GET /me`); a 401 clears the session quietly.
    provideAppInitializer(() => {
      inject(AuthService).bootstrapSession();
    }),
  ],
};
