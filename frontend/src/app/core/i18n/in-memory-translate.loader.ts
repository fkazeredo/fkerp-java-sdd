import { TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { DEFAULT_LANG, TRANSLATIONS } from './translations';

/**
 * {@link TranslateLoader} that serves the in-app {@link TRANSLATIONS} synchronously (no HTTP),
 * falling back to the default language for unknown locales (DL-0003).
 */
export class InMemoryTranslateLoader extends TranslateLoader {
  override getTranslation(lang: string): Observable<Record<string, Record<string, string>>> {
    return of(TRANSLATIONS[lang] ?? TRANSLATIONS[DEFAULT_LANG]);
  }
}
