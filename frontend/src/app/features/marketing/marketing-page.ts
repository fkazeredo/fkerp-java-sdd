import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CadastroLabelPipe } from '../../core/cadastro/cadastro-label.pipe';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  AttributionView,
  CampaignSendResult,
  CampaignStatus,
  CampaignView,
  ConsentStateResponse,
  ConsentStatus,
  ErasureResult,
  LegalBasis,
  SegmentPreviewResponse,
  SegmentView,
  SubjectType,
} from './marketing.models';
import { MarketingService } from './marketing.service';

/**
 * Marketing screen (SPEC-0019, SPEC-0029 16c): LGPD consent, segments, campaigns and attribution.
 * Reads a subject's current consent state and its append-only history, grants/revokes consent;
 * defines a segment and previews its reach; creates a campaign and dispatches it (the send filters by
 * consent — BR2 — and reports targeted/suppressed/queued); registers/lists a campaign→booking
 * attribution; and runs the LGPD erasure (PII removed, revocation tombstone preserved — BR6). Each
 * data section uses {@link ScreenState} for the loading/empty/error/permission states.
 */
@Component({
  selector: 'app-marketing-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
    CadastroLabelPipe,
  ],
  templateUrl: './marketing-page.html',
})
export class MarketingPage implements FormLeaveGuard {
  private readonly marketingService = inject(MarketingService);

  readonly subjectTypes: SubjectType[] = ['ACCOUNT', 'AGENT'];
  readonly legalBases: LegalBasis[] = ['CONSENT', 'LEGITIMATE_INTEREST'];

  // Consent lookup + history.
  consentSubject = '';
  consentSubjectType: SubjectType = 'ACCOUNT';
  readonly consentState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly consent = signal<ConsentStateResponse | null>(null);
  readonly consentError = signal<string | null>(null);

  readonly historyState = computed<ScreenStateKind>(() => {
    const s = this.consentState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return (this.consent()?.history.length ?? 0) === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Grant consent form.
  grantLegalBasis: LegalBasis = 'CONSENT';
  grantSource = '';
  readonly grantBusy = signal(false);
  readonly grantError = signal<string | null>(null);

  // Segment define + preview.
  segmentName = '';
  segmentCriteria = '';
  readonly segmentBusy = signal(false);
  readonly segmentError = signal<string | null>(null);
  readonly segment = signal<SegmentView | null>(null);
  readonly preview = signal<SegmentPreviewResponse | null>(null);

  // Campaign.
  campaignSegmentId = '';
  campaignCode = '';
  campaignContentRef = '';
  readonly campaignBusy = signal(false);
  readonly campaignError = signal<string | null>(null);
  readonly campaign = signal<CampaignView | null>(null);
  readonly sendResult = signal<CampaignSendResult | null>(null);

  // Attribution.
  attributionCode = '';
  attributionBooking = '';
  readonly attributionState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly attributions = signal<AttributionView[]>([]);
  readonly attributionError = signal<string | null>(null);

  readonly attributionListState = computed<ScreenStateKind>(() => {
    const s = this.attributionState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return this.attributions().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // LGPD erasure.
  erasureSubject = '';
  erasureSubjectType: SubjectType = 'ACCOUNT';
  readonly erasureBusy = signal(false);
  readonly erasureError = signal<string | null>(null);
  readonly erasureResult = signal<ErasureResult | null>(null);

  /** Whether an authoring form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return (
      (!this.segmentBusy() && (!!this.segmentName || !!this.segmentCriteria)) ||
      (!this.campaignBusy() && (!!this.campaignCode || !!this.campaignSegmentId))
    );
  }

  /** Looks up a subject's current consent state and history. */
  lookupConsent(): void {
    const subject = this.consentSubject.trim();
    if (!subject) {
      return;
    }
    this.consentState.set('loading');
    this.consentError.set(null);
    this.marketingService.consentState(subject, this.consentSubjectType).subscribe({
      next: (state) => {
        this.consent.set(state);
        this.consentState.set('success');
      },
      error: (error: ApiError) => {
        this.consentError.set(error?.code ?? 'error.internal');
        this.consentState.set('error');
      },
    });
  }

  /** Grants consent for the looked-up subject and refreshes the history. */
  grantConsent(): void {
    const subject = this.consentSubject.trim();
    if (!subject) {
      return;
    }
    this.grantBusy.set(true);
    this.grantError.set(null);
    this.marketingService
      .grantConsent({
        subject: { id: subject, type: this.consentSubjectType },
        purpose: 'NEWSLETTER',
        legalBasis: this.grantLegalBasis,
        source: this.grantSource || null,
      })
      .subscribe({
        next: () => {
          this.grantBusy.set(false);
          this.grantSource = '';
          this.lookupConsent();
        },
        error: (error: ApiError) => {
          this.grantBusy.set(false);
          this.grantError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Revokes a consent row by id and refreshes the history. */
  revokeConsent(id: string): void {
    this.grantBusy.set(true);
    this.grantError.set(null);
    this.marketingService.revokeConsent(id).subscribe({
      next: () => {
        this.grantBusy.set(false);
        this.lookupConsent();
      },
      error: (error: ApiError) => {
        this.grantBusy.set(false);
        this.grantError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** Defines a segment (criteria parsed as `field=value` pairs) and previews its reach. */
  defineSegment(): void {
    if (!this.segmentName) {
      return;
    }
    this.segmentBusy.set(true);
    this.segmentError.set(null);
    this.marketingService
      .defineSegment({ name: this.segmentName, criteria: this.parseCriteria() })
      .subscribe({
        next: (segment) => {
          this.segmentBusy.set(false);
          this.segment.set(segment);
          this.campaignSegmentId = segment.id;
          this.previewSegment(segment.id);
        },
        error: (error: ApiError) => {
          this.segmentBusy.set(false);
          this.segmentError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Previews a segment's estimated reach. */
  previewSegment(id: string): void {
    this.marketingService.previewSegment(id).subscribe({
      next: (preview) => this.preview.set(preview),
      error: (error: ApiError) => this.segmentError.set(error?.code ?? 'error.internal'),
    });
  }

  /** Creates a campaign targeting the entered segment. */
  createCampaign(): void {
    if (!this.campaignSegmentId || !this.campaignCode) {
      return;
    }
    this.campaignBusy.set(true);
    this.campaignError.set(null);
    this.sendResult.set(null);
    this.marketingService
      .createCampaign({
        segmentId: this.campaignSegmentId,
        code: this.campaignCode,
        contentRef: this.campaignContentRef || null,
      })
      .subscribe({
        next: (campaign) => {
          this.campaignBusy.set(false);
          this.campaign.set(campaign);
        },
        error: (error: ApiError) => {
          this.campaignBusy.set(false);
          this.campaignError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Dispatches the created campaign (consent-filtered; idempotent). */
  sendCampaign(): void {
    const current = this.campaign();
    if (!current) {
      return;
    }
    this.campaignBusy.set(true);
    this.campaignError.set(null);
    this.marketingService.sendCampaign(current.id).subscribe({
      next: (result) => {
        this.campaignBusy.set(false);
        this.sendResult.set(result);
        this.marketingService.getCampaign(current.id).subscribe((c) => this.campaign.set(c));
      },
      error: (error: ApiError) => {
        this.campaignBusy.set(false);
        this.campaignError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** Registers a campaign→booking attribution and reloads the list. */
  registerAttribution(): void {
    if (!this.attributionCode || !this.attributionBooking) {
      return;
    }
    this.attributionError.set(null);
    this.marketingService
      .registerAttribution({
        campaignCode: this.attributionCode,
        bookingId: this.attributionBooking,
      })
      .subscribe({
        next: () => {
          this.attributionBooking = '';
          this.loadAttribution();
        },
        error: (error: ApiError) => this.attributionError.set(error?.code ?? 'error.internal'),
      });
  }

  /** Lists attributions for the entered campaign code. */
  loadAttribution(): void {
    const code = this.attributionCode.trim();
    if (!code) {
      return;
    }
    this.attributionState.set('loading');
    this.attributionError.set(null);
    this.marketingService.attribution(code).subscribe({
      next: (rows) => {
        this.attributions.set(rows);
        this.attributionState.set('success');
      },
      error: (error: ApiError) => {
        this.attributionError.set(error?.code ?? 'error.internal');
        this.attributionState.set('error');
      },
    });
  }

  /** Runs the LGPD erasure for the entered subject. */
  erase(): void {
    const subject = this.erasureSubject.trim();
    if (!subject) {
      return;
    }
    this.erasureBusy.set(true);
    this.erasureError.set(null);
    this.marketingService
      .erase({ subjectId: subject, subjectType: this.erasureSubjectType })
      .subscribe({
        next: (result) => {
          this.erasureBusy.set(false);
          this.erasureResult.set(result);
        },
        error: (error: ApiError) => {
          this.erasureBusy.set(false);
          this.erasureError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Parses the free-text criteria (`field=value` per line) into a map. */
  private parseCriteria(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const line of this.segmentCriteria.split(/[\n,]/)) {
      const [field, ...rest] = line.split('=');
      const key = field?.trim();
      if (key && rest.length > 0) {
        map[key] = rest.join('=').trim();
      }
    }
    return map;
  }

  /** PrimeNG Tag severity for a consent status. */
  consentSeverity(status: ConsentStatus): 'success' | 'danger' {
    return status === 'GRANTED' ? 'success' : 'danger';
  }

  /** PrimeNG Tag severity for a campaign status. */
  campaignSeverity(status: CampaignStatus): 'success' | 'secondary' {
    return status === 'SENT' ? 'success' : 'secondary';
  }
}
