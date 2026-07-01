import { Money } from '../../core/models/api.models';

/** Where a sourced offer comes from (SPEC-0009 BR1). */
export type OfferOrigin =
  | 'PORTAL_API'
  | 'EXTERNAL_SITE'
  | 'THIRD_PARTY_CATALOG'
  | 'RAW_DEMAND';

/** How integrated the source of an offer is (SPEC-0009 BR1). */
export type IntegrationLevel = 'NONE' | 'INBOUND' | 'BIDIRECTIONAL';

/** Read view of a sourced offer (SPEC-0009). */
export interface SourcedOfferView {
  id: string;
  productText: string;
  basePrice: Money;
  origin: OfferOrigin;
  integrationLevel: IntegrationLevel;
  externalRef: string | null;
  createdAt: string;
}

/** Body for `POST /api/sourcing/offers`. */
export interface RegisterSourcedOfferRequest {
  productText: string;
  basePrice: Money;
  origin: OfferOrigin;
  integrationLevel: IntegrationLevel;
  externalRef?: string | null;
}
