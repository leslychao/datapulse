export interface SearchResult {
  products: SearchProduct[];
  policies: SearchPolicy[];
  promos: SearchPromo[];
  views: SearchView[];
}

export interface SearchProduct {
  offerId: number;
  skuCode: string;
  productName: string;
  marketplaceType: string;
}

export interface SearchPolicy {
  policyId: number;
  name: string;
}

export interface SearchPromo {
  campaignId: number;
  name: string;
}

export interface SearchView {
  viewId: number;
  name: string;
}
