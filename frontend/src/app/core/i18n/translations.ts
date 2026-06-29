/**
 * In-app translation dictionaries (pt-BR default, en fallback). Kept in code for the foundation so
 * the i18n seam works without an HTTP fetch (robust in tests). A future slice may move these to
 * fetched JSON assets if runtime-editable translations are needed (DL-0003).
 */
export const TRANSLATIONS: Record<string, Record<string, Record<string, string>>> = {
  'pt-BR': {
    health: {
      title: 'Saúde do sistema',
      loading: 'Verificando…',
      status: 'Status',
      db: 'Banco de dados',
      ok: 'Sistema saudável',
      error: 'Não foi possível contatar o backend',
      retry: 'Tentar novamente',
    },
  },
  en: {
    health: {
      title: 'System health',
      loading: 'Checking…',
      status: 'Status',
      db: 'Database',
      ok: 'Service is healthy',
      error: 'Could not reach the backend',
      retry: 'Retry',
    },
  },
};

/** Default project locale (pt-BR). */
export const DEFAULT_LANG = 'pt-BR';
