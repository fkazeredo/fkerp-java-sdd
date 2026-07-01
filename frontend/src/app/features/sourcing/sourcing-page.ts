import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState } from '../../shared/screen-state/screen-state';
import { IntegrationLevel, OfferOrigin, SourcedOfferView } from './sourcing.models';
import { SourcingService } from './sourcing.service';

/**
 * Sourcing screen (SPEC-0009, SPEC-0029 16b): registers a sourced offer's provenance (product text +
 * base price + origin + integration level + optional external ref) and looks one up by id, showing
 * the hybrid world's origin (own portal / external site / third-party catalog / raw demand) and how
 * integrated the source is (none / inbound / bidirectional). The offer list is not exposed by the API
 * (there is no list endpoint — DL-0109/Scope), so the screen reads a single offer by id. The lookup
 * section uses {@link ScreenState} for the loading/error/permission states.
 */
@Component({
  selector: 'app-sourcing-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './sourcing-page.html',
})
export class SourcingPage implements FormLeaveGuard {
  private readonly sourcingService = inject(SourcingService);

  readonly origins: OfferOrigin[] = [
    'PORTAL_API',
    'EXTERNAL_SITE',
    'THIRD_PARTY_CATALOG',
    'RAW_DEMAND',
  ];
  readonly integrationLevels: IntegrationLevel[] = ['NONE', 'INBOUND', 'BIDIRECTIONAL'];

  readonly format = formatMoney;

  // Register form.
  newProductText = '';
  newAmount: number | null = null;
  newCurrency = 'BRL';
  newOrigin: OfferOrigin = 'EXTERNAL_SITE';
  newIntegration: IntegrationLevel = 'NONE';
  newExternalRef = '';
  readonly registering = signal(false);
  readonly registerError = signal<string | null>(null);

  // Lookup state.
  lookupId = '';
  readonly offer = signal<SourcedOfferView | null>(null);
  readonly state = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly errorCode = signal<string | null>(null);

  /** Whether the register form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.registering() && (!!this.newProductText || this.newAmount != null);
  }

  /** Registers a sourced offer and shows it in the lookup panel. */
  register(): void {
    if (!this.newProductText || this.newAmount == null) {
      return;
    }
    this.registering.set(true);
    this.registerError.set(null);
    this.sourcingService
      .register({
        productText: this.newProductText,
        basePrice: { amount: this.newAmount, currency: this.newCurrency },
        origin: this.newOrigin,
        integrationLevel: this.newIntegration,
        externalRef: this.newExternalRef || null,
      })
      .subscribe({
        next: (registered) => {
          this.registering.set(false);
          this.newProductText = '';
          this.newAmount = null;
          this.newExternalRef = '';
          this.offer.set(registered);
          this.state.set('success');
          this.lookupId = registered.id;
        },
        error: (error: ApiError) => {
          this.registering.set(false);
          this.registerError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Looks up a sourced offer by id. */
  lookup(): void {
    const id = this.lookupId.trim();
    if (!id) {
      return;
    }
    this.state.set('loading');
    this.errorCode.set(null);
    this.sourcingService.getById(id).subscribe({
      next: (offer) => {
        this.offer.set(offer);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }

  /** PrimeNG Tag severity for the integration level. */
  integrationSeverity(level: IntegrationLevel): 'success' | 'info' | 'secondary' {
    switch (level) {
      case 'BIDIRECTIONAL':
        return 'success';
      case 'INBOUND':
        return 'info';
      default:
        return 'secondary';
    }
  }
}
