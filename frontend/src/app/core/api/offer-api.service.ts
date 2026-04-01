import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  ActionHistoryEntry,
  BulkActionResponse,
  BulkManualPreviewRequest,
  BulkManualPreviewResponse,
  GridKpi,
  LockPriceRequest,
  OfferDetail,
  OfferFilter,
  OfferSummary,
  Page,
  PriceJournalEntry,
  PromoJournalEntry,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class OfferApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listOffers(
      workspaceId: number,
      filter: OfferFilter,
      page: number,
      size: number,
      sort?: string,
      direction?: string,
  ): Observable<Page<OfferSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (sort) params = params.set('sort', sort);
    if (direction) params = params.set('direction', direction);

    if (filter.marketplaceType?.length) {
      params = params.set('marketplace_type', filter.marketplaceType.join(','));
    }
    if (filter.connectionId?.length) {
      params = params.set('connection_id', filter.connectionId.join(','));
    }
    if (filter.categoryId?.length) {
      params = params.set('category_id', filter.categoryId.join(','));
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.skuCode) {
      params = params.set('sku_code', filter.skuCode);
    }
    if (filter.productName) {
      params = params.set('product_name', filter.productName);
    }
    if (filter.marginMin !== undefined && filter.marginMin !== null) {
      params = params.set('margin_min', filter.marginMin);
    }
    if (filter.marginMax !== undefined && filter.marginMax !== null) {
      params = params.set('margin_max', filter.marginMax);
    }
    if (filter.stockRisk?.length) {
      params = params.set('stock_risk', filter.stockRisk.join(','));
    }
    if (filter.hasManualLock !== undefined) {
      params = params.set('has_manual_lock', filter.hasManualLock);
    }
    if (filter.hasActivePromo !== undefined) {
      params = params.set('has_active_promo', filter.hasActivePromo);
    }
    if (filter.lastDecision?.length) {
      params = params.set('last_decision', filter.lastDecision.join(','));
    }
    if (filter.lastActionStatus?.length) {
      params = params.set('last_action_status', filter.lastActionStatus.join(','));
    }

    return this.http.get<Page<OfferSummary>>(
      `${this.base}/workspaces/${workspaceId}/grid`,
      { params },
    );
  }

  getOffer(workspaceId: number, offerId: number): Observable<OfferDetail> {
    return this.http.get<OfferDetail>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}`,
    );
  }

  getGridKpi(workspaceId: number): Observable<GridKpi> {
    return this.http.get<GridKpi>(
      `${this.base}/workspaces/${workspaceId}/grid/kpi`,
    );
  }

  exportOffers(
      workspaceId: number,
      filter: OfferFilter,
      format: 'csv' | 'xlsx' = 'csv',
  ): Observable<Blob> {
    let params = new HttpParams().set('format', format);

    if (filter.marketplaceType?.length) {
      params = params.set('marketplace_type', filter.marketplaceType.join(','));
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.skuCode) {
      params = params.set('sku_code', filter.skuCode);
    }
    if (filter.productName) {
      params = params.set('product_name', filter.productName);
    }

    return this.http.get(
      `${this.base}/workspaces/${workspaceId}/grid/export`,
      { params, responseType: 'blob' },
    );
  }

  getPriceJournal(
      workspaceId: number,
      offerId: number,
      page = 0,
      size = 20,
      decisionType?: string,
  ): Observable<Page<PriceJournalEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (decisionType) params = params.set('decisionType', decisionType);
    return this.http.get<Page<PriceJournalEntry>>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}/price-journal`,
      { params },
    );
  }

  getPromoJournal(
      workspaceId: number,
      offerId: number,
      page = 0,
      size = 20,
  ): Observable<Page<PromoJournalEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<PromoJournalEntry>>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}/promo-journal`,
      { params },
    );
  }

  getActionHistory(
      workspaceId: number,
      offerId: number,
      page = 0,
      size = 20,
  ): Observable<Page<ActionHistoryEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ActionHistoryEntry>>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}/action-history`,
      { params },
    );
  }

  lockOffer(workspaceId: number, offerId: number, req: LockPriceRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}/lock`, req,
    );
  }

  unlockOffer(workspaceId: number, offerId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/offers/${offerId}/unlock`, {},
    );
  }

  bulkManualPreview(workspaceId: number, req: BulkManualPreviewRequest): Observable<BulkManualPreviewResponse> {
    return this.http.post<BulkManualPreviewResponse>(
      `${this.base}/workspaces/${workspaceId}/pricing/bulk-manual/preview`, req,
    );
  }

  bulkManualApply(workspaceId: number, req: BulkManualPreviewRequest): Observable<BulkActionResponse> {
    return this.http.post<BulkActionResponse>(
      `${this.base}/workspaces/${workspaceId}/pricing/bulk-manual/apply`, req,
    );
  }
}
