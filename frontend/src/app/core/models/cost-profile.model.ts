/** SKU autocomplete for adding cost (catalog seller_sku, not only existing profiles). */
export interface SellerSkuSuggestion {
  sellerSkuId: number;
  skuCode: string;
  productName: string;
}

export interface CostProfile {
  id: number;
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  costPrice: number | null;
  currency: string;
  validFrom: string;
  updatedAt: string | null;
}

export interface CostProfilePage {
  content: CostProfile[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateCostProfileRequest {
  sellerSkuId: number;
  costPrice: number;
  currency: string;
  validFrom: string;
}

export interface UpdateCostProfileRequest {
  costPrice: number;
  currency: string;
  validFrom: string;
}

export interface CostProfileImportResult {
  imported: number;
  skipped: number;
  errors: CostProfileImportError[];
}

export interface CostProfileImportError {
  row: number;
  message: string;
}

export type CostUpdateOperation = 'FIXED' | 'INCREASE_PCT' | 'DECREASE_PCT' | 'MULTIPLY';

export interface BulkFormulaCostRequest {
  sellerSkuIds: number[];
  operation: CostUpdateOperation;
  value: number;
  validFrom: string;
}

export interface BulkFormulaCostResponse {
  updated: number;
  skipped: number;
  errors: string[];
}
