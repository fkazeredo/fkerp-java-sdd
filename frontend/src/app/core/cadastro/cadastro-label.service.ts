import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../config/api';
import { CadastroItemView, CadastroType } from '../../features/cadastro/cadastro.models';

/**
 * Shared read-only lookup of cadastro labels (SPEC-0031/DL-0116): fetches `GET
 * /api/cadastro/items?type=…` once per type, caches the code→label map, and exposes a synchronous
 * {@link label} that the screens (via {@link CadastroLabelPipe}) use to render the human label
 * instead of the raw code. Until a type has loaded, {@link label} returns the code unchanged, so the
 * UI degrades gracefully (never a blank cell). This retro-fixes the seam slice 18a left (screens
 * showing the code) and covers the 18b Marketing/Intelligence/Portfolio screens.
 *
 * <p>It is a projection: reading a label changes nothing, and the backend stays the authority — the
 * lookup only maps codes to labels for display. Inactive items are still mapped (a persisted code
 * may reference a now-inactive item; showing its label is correct for a historical value).
 */
@Injectable({ providedIn: 'root' })
export class CadastroLabelService {
  private readonly http = inject(HttpClient);

  /** Per-type code→label maps, as a signal so the pipe re-renders when a type finishes loading. */
  private readonly maps = signal<Record<string, Record<string, string>>>({});

  /** The types whose fetch has already been kicked off (avoids duplicate requests). */
  private readonly requested = new Set<CadastroType>();

  /** The reactive map registry (read-only) — the pipe reads it to resolve labels. */
  get labels(): Signal<Record<string, Record<string, string>>> {
    return this.maps.asReadonly();
  }

  /**
   * Ensures a type's items are loaded (idempotent): triggers the fetch the first time a type is
   * seen, then caches the code→label map. Safe to call from a pipe on every change detection.
   */
  ensureLoaded(type: CadastroType): void {
    if (this.requested.has(type)) {
      return;
    }
    this.requested.add(type);
    const params = new HttpParams().set('type', type);
    this.http
      .get<CadastroItemView[]>(`${API_BASE_URL}/cadastro/items`, { params })
      .subscribe({
        next: (items) => {
          const map: Record<string, string> = {};
          for (const item of items) {
            map[item.code] = item.label;
          }
          this.maps.update((current) => ({ ...current, [type]: map }));
        },
        error: () => {
          // Leave the type unmapped on error — label() falls back to the code (never blocks the UI).
          this.requested.delete(type);
        },
      });
  }

  /**
   * The human label for a code of a type, or the code itself when the type has not loaded yet or the
   * code is unknown (graceful fallback). Reads the reactive map, so a caller inside a template
   * updates once the fetch resolves.
   */
  label(type: CadastroType, code: string | null | undefined): string {
    if (!code) {
      return '';
    }
    return this.maps()[type]?.[code] ?? code;
  }
}
