import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { CadastroItemView, CadastroType } from './cadastro.models';
import { CadastroService } from './cadastro.service';

/**
 * Cadastros screen (SPEC-0031; ADR-0019): editable reference data. Lists the convertible types;
 * for a selected type it lists the items and (with ROLE_POLICY_ADMIN) adds a new code, edits a
 * label/order and activates/deactivates an item. Writes require ROLE_POLICY_ADMIN — a caller
 * without it gets a 403 rendered as the permission state by {@link ScreenState}. The backend is the
 * authority; this screen never invents labels (SPEC-0026 BR8).
 */
@Component({
  selector: 'app-cadastro-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './cadastro-page.html',
})
export class CadastroPage implements OnInit {
  private readonly cadastroService = inject(CadastroService);

  readonly types = signal<CadastroType[]>([]);
  selectedType: CadastroType | null = null;

  readonly listState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly items = signal<CadastroItemView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly itemsState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return this.items().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Create-item form.
  newCode = '';
  newLabel = '';
  newSortOrder = 0;
  readonly createBusy = signal(false);
  readonly createError = signal<string | null>(null);

  ngOnInit(): void {
    this.cadastroService.listTypes().subscribe({
      next: (types) => {
        this.types.set(types);
        if (types.length > 0) {
          this.selectedType = types[0];
          this.loadItems();
        }
      },
      error: (error: ApiError) => this.errorCode.set(error?.code ?? 'error.internal'),
    });
  }

  /** (Re)loads the items of the selected type. */
  loadItems(): void {
    if (!this.selectedType) {
      return;
    }
    this.listState.set('loading');
    this.errorCode.set(null);
    this.cadastroService.listItems(this.selectedType).subscribe({
      next: (items) => {
        this.items.set(items);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Creates a new code for the selected type and reloads. */
  createItem(): void {
    if (!this.selectedType || !this.newCode || !this.newLabel) {
      return;
    }
    this.createBusy.set(true);
    this.createError.set(null);
    this.cadastroService
      .create({
        type: this.selectedType,
        code: this.newCode,
        label: this.newLabel,
        sortOrder: this.newSortOrder,
      })
      .subscribe({
        next: () => {
          this.createBusy.set(false);
          this.newCode = '';
          this.newLabel = '';
          this.newSortOrder = 0;
          this.loadItems();
        },
        error: (error: ApiError) => {
          this.createBusy.set(false);
          this.createError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Saves an edited label/order for an item. */
  saveItem(item: CadastroItemView): void {
    this.cadastroService
      .update(item.id, { label: item.label, active: item.active, sortOrder: item.sortOrder })
      .subscribe({
        next: () => this.loadItems(),
        error: (error: ApiError) => this.errorCode.set(error?.code ?? 'error.internal'),
      });
  }

  /** Toggles active/inactive for an item. */
  toggleActive(item: CadastroItemView): void {
    if (item.active) {
      this.cadastroService.deactivate(item.id).subscribe({
        next: () => this.loadItems(),
        error: (error: ApiError) => this.errorCode.set(error?.code ?? 'error.internal'),
      });
    } else {
      this.cadastroService
        .update(item.id, { label: item.label, active: true, sortOrder: item.sortOrder })
        .subscribe({
          next: () => this.loadItems(),
          error: (error: ApiError) => this.errorCode.set(error?.code ?? 'error.internal'),
        });
    }
  }

  /** PrimeNG Tag severity for an item's active flag. */
  activeSeverity(active: boolean): 'success' | 'secondary' {
    return active ? 'success' : 'secondary';
  }
}
