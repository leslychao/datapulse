import { Routes } from '@angular/router';

import { BiddingLayoutComponent } from './bidding-layout.component';
import { biddingWriteGuard } from './bidding-write.guard';
import { biddingUnsavedChangesGuard } from './bidding-unsaved-changes.guard';

const routes: Routes = [
  {
    path: '',
    component: BiddingLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./dashboard/bidding-dashboard-page.component').then(
            (m) => m.BiddingDashboardPageComponent,
          ),
        data: { breadcrumb: 'Дашборд' },
      },
      {
        path: 'strategies',
        loadComponent: () =>
          import('./strategies/bid-policy-list-page.component').then(
            (m) => m.BidPolicyListPageComponent,
          ),
        data: { breadcrumb: 'Стратегии' },
      },
      {
        path: 'strategies/new',
        canActivate: [biddingWriteGuard],
        canDeactivate: [biddingUnsavedChangesGuard],
        loadComponent: () =>
          import('./strategies/bid-policy-form-page.component').then(
            (m) => m.BidPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Новая стратегия' },
      },
      {
        path: 'strategies/:policyId/edit',
        canActivate: [biddingWriteGuard],
        canDeactivate: [biddingUnsavedChangesGuard],
        loadComponent: () =>
          import('./strategies/bid-policy-form-page.component').then(
            (m) => m.BidPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Редактирование' },
      },
      {
        path: 'runs',
        loadComponent: () =>
          import('./runs/bidding-runs-list-page.component').then(
            (m) => m.BiddingRunsListPageComponent,
          ),
        data: { breadcrumb: 'Прогоны' },
      },
      {
        path: 'runs/:runId',
        loadComponent: () =>
          import('./runs/bidding-run-detail-page.component').then(
            (m) => m.BiddingRunDetailPageComponent,
          ),
        data: { breadcrumb: 'Детали прогона' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/bid-decisions-list-page.component').then(
            (m) => m.BidDecisionsListPageComponent,
          ),
        data: { breadcrumb: 'Решения' },
      },
      {
        path: 'decisions/:decisionId',
        loadComponent: () =>
          import('./decisions/bid-decision-detail-page.component').then(
            (m) => m.BidDecisionDetailPageComponent,
          ),
        data: { breadcrumb: 'Решение' },
      },
      {
        path: 'actions',
        loadComponent: () =>
          import('./actions/bid-actions-list-page.component').then(
            (m) => m.BidActionsListPageComponent,
          ),
        data: { breadcrumb: 'Действия' },
      },
      {
        path: 'locks',
        loadComponent: () =>
          import('./locks/bid-locks-page.component').then(
            (m) => m.BidLocksPageComponent,
          ),
        data: { breadcrumb: 'Блокировки' },
      },
    ],
  },
];

export default routes;
