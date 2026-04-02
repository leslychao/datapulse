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
