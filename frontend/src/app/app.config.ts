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
import { provideOAuthClient } from 'angular-oauth2-oidc';
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
    // OIDC client (SPEC-0024 Phase 13 / DL-0106). The bearer header is attached by the app's own
    // authInterceptor, so the library's resource-server token injection stays off (sendAccessToken
    // false) to avoid double-attaching.
    provideOAuthClient({
      resourceServer: { allowedUrls: ['/api'], sendAccessToken: false },
    }),
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
    // OIDC session bootstrap (SPEC-0024 Phase 13, DL-0106): configure the IdP client, complete the
    // login if returning from the IdP with a code, enable real silent-refresh (refresh token) and
    // confirm the session against the backend (`GET /me`). Returns the promise so Angular waits for it.
    provideAppInitializer(() => inject(AuthService).bootstrapSession()),
  ],
};
