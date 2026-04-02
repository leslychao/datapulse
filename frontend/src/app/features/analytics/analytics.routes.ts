import { Routes } from '@angular/router';

import { AnalyticsLayoutComponent } from './analytics-layout.component';

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
        data: { breadcrumb: 'Сводка', section: 'pnl' },
      },
      {
        path: 'pnl/by-product',
        loadComponent: () =>
          import('./pnl/pnl-by-product-page.component').then(
            (m) => m.PnlByProductPageComponent,
          ),
        data: { breadcrumb: 'По товарам', section: 'pnl' },
      },
      {
        path: 'pnl/by-posting',
        loadComponent: () =>
          import('./pnl/pnl-by-posting-page.component').then(
            (m) => m.PnlByPostingPageComponent,
          ),
        data: { breadcrumb: 'По отправкам', section: 'pnl' },
      },
      {
        path: 'pnl/posting/:postingId',
        loadComponent: () =>
          import('./pnl/posting-detail-page.component').then(
            (m) => m.PostingDetailPageComponent,
          ),
        data: { breadcrumb: 'Детали', section: 'pnl' },
      },
      {
        path: 'pnl/trend',
        loadComponent: () =>
          import('./pnl/pnl-trend-page.component').then(
            (m) => m.PnlTrendPageComponent,
          ),
        data: { breadcrumb: 'Тренд', section: 'pnl' },
      },

      // Inventory
      { path: 'inventory', redirectTo: 'inventory/overview', pathMatch: 'full' },
      {
        path: 'inventory/overview',
        loadComponent: () =>
          import('./inventory/inventory-overview-page.component').then(
            (m) => m.InventoryOverviewPageComponent,
          ),
        data: { breadcrumb: 'Обзор', section: 'inventory' },
      },
      {
        path: 'inventory/by-product',
        loadComponent: () =>
          import('./inventory/inventory-by-product-page.component').then(
            (m) => m.InventoryByProductPageComponent,
          ),
        data: { breadcrumb: 'По товарам', section: 'inventory' },
      },
      {
        path: 'inventory/stock-history',
        loadComponent: () =>
          import('./inventory/stock-history-page.component').then(
            (m) => m.StockHistoryPageComponent,
          ),
        data: { breadcrumb: 'История', section: 'inventory' },
      },

      // Returns
      { path: 'returns', redirectTo: 'returns/summary', pathMatch: 'full' },
      {
        path: 'returns/summary',
        loadComponent: () =>
          import('./returns/returns-summary-page.component').then(
            (m) => m.ReturnsSummaryPageComponent,
          ),
        data: { breadcrumb: 'Сводка', section: 'returns' },
      },
      {
        path: 'returns/by-product',
        loadComponent: () =>
          import('./returns/returns-by-product-page.component').then(
            (m) => m.ReturnsByProductPageComponent,
          ),
        data: { breadcrumb: 'По товарам', section: 'returns' },
      },
      {
        path: 'returns/trend',
        loadComponent: () =>
          import('./returns/returns-trend-page.component').then(
            (m) => m.ReturnsTrendPageComponent,
          ),
        data: { breadcrumb: 'Тренд', section: 'returns' },
      },

      // Data Quality
      { path: 'data-quality', redirectTo: 'data-quality/status', pathMatch: 'full' },
      {
        path: 'data-quality/status',
        loadComponent: () =>
          import('./data-quality/data-quality-status-page.component').then(
            (m) => m.DataQualityStatusPageComponent,
          ),
        data: { breadcrumb: 'Статус', section: 'data-quality' },
      },
      {
        path: 'data-quality/reconciliation',
        loadComponent: () =>
          import('./data-quality/reconciliation-page.component').then(
            (m) => m.ReconciliationPageComponent,
          ),
        data: { breadcrumb: 'Reconciliation', section: 'data-quality' },
      },
    ],
  },
];

export default routes;
