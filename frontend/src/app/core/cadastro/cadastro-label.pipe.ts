import { Pipe, PipeTransform, inject } from '@angular/core';
import { CadastroType } from '../../features/cadastro/cadastro.models';
import { CadastroLabelService } from './cadastro-label.service';

/**
 * Renders a cadastro code as its human label (SPEC-0031/DL-0116): {@code {{ code | cadastroLabel:'INSIGHT_TYPE' }}}.
 * It triggers the (idempotent) per-type load on first use and returns the label once loaded,
 * falling back to the code until then (or when the code is unknown). Impure so it re-evaluates when
 * the underlying label map finishes loading — the lookup is a cheap cached map read.
 */
@Pipe({ name: 'cadastroLabel', pure: false })
export class CadastroLabelPipe implements PipeTransform {
  private readonly labels = inject(CadastroLabelService);

  transform(code: string | null | undefined, type: CadastroType): string {
    this.labels.ensureLoaded(type);
    return this.labels.label(type, code);
  }
}
