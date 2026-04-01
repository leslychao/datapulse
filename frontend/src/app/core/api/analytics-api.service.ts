import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  AnalyticsFilter,
  DataQualityStatus,
  InventoryByProduct,
  InventoryOverview,
  PnlByPosting,
  PnlByProduct,
  PnlSummary,
  PnlTrendPoint,
  PostingDetail,
  ReconciliationResult,
  ReturnsByProduct,
  ReturnsSummary,
  ReturnsTrendPoint,
  StockHistoryPoint,
  Page,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  getPnlSummary(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<PnlSummary> {
    return this.http.get<PnlSummary>(
      `${this.base}/workspace/${workspaceId}/analytics/pnl/summary`,
      { params: this.buildParams(filter) },
    );
  }

  getPnlTrend(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<PnlTrendPoint[]> {
    return this.http.get<PnlTrendPoint[]>(
      `${this.base}/workspace/${workspaceId}/analytics/pnl/trend`,
      { params: this.buildParams(filter) },
    );
  }

  listPnlByProduct(
    workspaceId: number,
    filter: AnalyticsFilter,
    page: number,
    size: number,
    sort = 'full_pnl,desc',
  ): Observable<Page<PnlByProduct>> {
    let params = this.buildParams(filter)
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<Page<PnlByProduct>>(
      `${this.base}/workspace/${workspaceId}/analytics/pnl/by-product`,
      { params },
    );
  }

  listPnlByPosting(
    workspaceId: number,
    filter: AnalyticsFilter,
    page: number,
    size: number,
    sort = 'finance_date,desc',
  ): Observable<Page<PnlByPosting>> {
    let params = this.buildParams(filter)
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<Page<PnlByPosting>>(
      `${this.base}/workspace/${workspaceId}/analytics/pnl/by-posting`,
      { params },
    );
  }

  getPostingDetail(
    workspaceId: number,
    postingId: string,
  ): Observable<PostingDetail> {
    return this.http.get<PostingDetail>(
      `${this.base}/workspace/${workspaceId}/analytics/pnl/posting/${postingId}/details`,
    );
  }

  getInventoryOverview(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<InventoryOverview> {
    return this.http.get<InventoryOverview>(
      `${this.base}/workspace/${workspaceId}/analytics/inventory/overview`,
      { params: this.buildParams(filter) },
    );
  }

  listInventoryByProduct(
    workspaceId: number,
    filter: AnalyticsFilter,
    page: number,
    size: number,
    sort = 'stock_out_risk,asc',
  ): Observable<Page<InventoryByProduct>> {
    let params = this.buildParams(filter)
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<Page<InventoryByProduct>>(
      `${this.base}/workspace/${workspaceId}/analytics/inventory/by-product`,
      { params },
    );
  }

  getStockHistory(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<StockHistoryPoint[]> {
    return this.http.get<StockHistoryPoint[]>(
      `${this.base}/workspace/${workspaceId}/analytics/inventory/stock-history`,
      { params: this.buildParams(filter) },
    );
  }

  getReturnsSummary(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<ReturnsSummary> {
    return this.http.get<ReturnsSummary>(
      `${this.base}/workspace/${workspaceId}/analytics/returns/summary`,
      { params: this.buildParams(filter) },
    );
  }

  listReturnsByProduct(
    workspaceId: number,
    filter: AnalyticsFilter,
    page: number,
    size: number,
    sort = 'return_rate_pct,desc',
  ): Observable<Page<ReturnsByProduct>> {
    let params = this.buildParams(filter)
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<Page<ReturnsByProduct>>(
      `${this.base}/workspace/${workspaceId}/analytics/returns/by-product`,
      { params },
    );
  }

  getReturnsTrend(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<ReturnsTrendPoint[]> {
    return this.http.get<ReturnsTrendPoint[]>(
      `${this.base}/workspace/${workspaceId}/analytics/returns/trend`,
      { params: this.buildParams(filter) },
    );
  }

  getDataQualityStatus(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<DataQualityStatus> {
    return this.http.get<DataQualityStatus>(
      `${this.base}/workspace/${workspaceId}/analytics/data-quality/status`,
      { params: this.buildParams(filter) },
    );
  }

  getReconciliation(
    workspaceId: number,
    filter: AnalyticsFilter,
  ): Observable<ReconciliationResult> {
    return this.http.get<ReconciliationResult>(
      `${this.base}/workspace/${workspaceId}/analytics/data-quality/reconciliation`,
      { params: this.buildParams(filter) },
    );
  }

  getProvenanceRawUrl(
    workspaceId: number,
    entryId: number,
  ): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(
      `${this.base}/workspace/${workspaceId}/analytics/provenance/entry/${entryId}/raw`,
    );
  }

  private buildParams(filter: AnalyticsFilter): HttpParams {
    let params = new HttpParams();
    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    if (filter.period) {
      params = params.set('period', filter.period);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }
    if (filter.granularity) {
      params = params.set('granularity', filter.granularity);
    }
    if (filter.sellerSkuId) {
      params = params.set('sellerSkuId', filter.sellerSkuId);
    }
    if (filter.stockOutRisk) {
      params = params.set('stockOutRisk', filter.stockOutRisk);
    }
    if (filter.productId) {
      params = params.set('productId', filter.productId);
    }
    return params;
  }
}
