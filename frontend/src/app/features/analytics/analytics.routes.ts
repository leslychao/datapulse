import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

import { AnalyticsLayoutComponent } from './analytics-layout.component';

const reconciliationGuard: CanActivateFn = () => {
  const rbac = inject(RbacService);
  const router = inject(Router);
  const wsStore = inject(WorkspaceContextStore);
  if (rbac.isAdmin()) return true;
  return router.createUrlTree([
    '/workspace', wsStore.currentWorkspaceId(),
    'analytics', 'data-quality', 'status',
  ]);
};

const routes: Routes = [
  {
    path: '',
    component: AnalyticsLayoutComponent,
    children: [
      { path: '', redirectTo: 'pnl/summary', pathMatch: 'full' },

      // P&L
      { path: 'pnl', redirectTo: 'pnl/summary', pathMatch: 'full' },
      {
        path: 'pnl/summary',
        loadComponent: () =>
          import('./pnl/pnl-summary-page.component').then(
            (m) => m.PnlSummaryPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.summary', section: 'pnl' },
      },
      {
        path: 'pnl/by-product',
        loadComponent: () =>
          import('./pnl/pnl-by-product-page.component').then(
            (m) => m.PnlByProductPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.by_product', section: 'pnl' },
      },
      {
        path: 'pnl/by-posting',
        loadComponent: () =>
          import('./pnl/pnl-by-posting-page.component').then(
            (m) => m.PnlByPostingPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.by_posting', section: 'pnl' },
      },
      {
        path: 'pnl/posting/:postingId',
        loadComponent: () =>
          import('./pnl/posting-detail-page.component').then(
            (m) => m.PostingDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.details', section: 'pnl' },
      },
      {
        path: 'pnl/trend',
        loadComponent: () =>
          import('./pnl/pnl-trend-page.component').then(
            (m) => m.PnlTrendPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.trend', section: 'pnl' },
      },

      // Inventory
      { path: 'inventory', redirectTo: 'inventory/overview', pathMatch: 'full' },
      {
        path: 'inventory/overview',
        loadComponent: () =>
          import('./inventory/inventory-overview-page.component').then(
            (m) => m.InventoryOverviewPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.overview', section: 'inventory' },
      },
      {
        path: 'inventory/by-product',
        loadComponent: () =>
          import('./inventory/inventory-by-product-page.component').then(
            (m) => m.InventoryByProductPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.by_product', section: 'inventory' },
      },
      {
        path: 'inventory/stock-history',
        loadComponent: () =>
          import('./inventory/stock-history-page.component').then(
            (m) => m.StockHistoryPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.history', section: 'inventory' },
      },

      // Returns
      { path: 'returns', redirectTo: 'returns/overview', pathMatch: 'full' },
      {
        path: 'returns/overview',
        loadComponent: () =>
          import('./returns/returns-overview-page.component').then(
            (m) => m.ReturnsOverviewPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.overview', section: 'returns' },
      },
      {
        path: 'returns/by-product',
        loadComponent: () =>
          import('./returns/returns-by-product-page.component').then(
            (m) => m.ReturnsByProductPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.products', section: 'returns' },
      },
      {
        path: 'returns/reasons',
        loadComponent: () =>
          import('./returns/returns-reasons-page.component').then(
            (m) => m.ReturnsReasonsPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.reasons', section: 'returns' },
      },

      // Data Quality
      { path: 'data-quality', redirectTo: 'data-quality/status', pathMatch: 'full' },
      {
        path: 'data-quality/status',
        loadComponent: () =>
          import('./data-quality/data-quality-status-page.component').then(
            (m) => m.DataQualityStatusPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.overview', section: 'data-quality' },
      },
      {
        path: 'data-quality/reconciliation',
        canActivate: [reconciliationGuard],
        loadComponent: () =>
          import('./data-quality/reconciliation-page.component').then(
            (m) => m.ReconciliationPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.analytics.reconciliation', section: 'data-quality' },
      },
    ],
  },
];

export default routes;
