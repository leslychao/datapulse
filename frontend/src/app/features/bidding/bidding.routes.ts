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
        data: { breadcrumb: 'breadcrumb.bidding.dashboard' },
      },
      {
        path: 'strategies',
        loadComponent: () =>
          import('./strategies/bid-policy-list-page.component').then(
            (m) => m.BidPolicyListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.strategies' },
      },
      {
        path: 'strategies/new',
        canActivate: [biddingWriteGuard],
        canDeactivate: [biddingUnsavedChangesGuard],
        loadComponent: () =>
          import('./strategies/bid-policy-form-page.component').then(
            (m) => m.BidPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.new_strategy' },
      },
      {
        path: 'strategies/:policyId/edit',
        canActivate: [biddingWriteGuard],
        canDeactivate: [biddingUnsavedChangesGuard],
        loadComponent: () =>
          import('./strategies/bid-policy-form-page.component').then(
            (m) => m.BidPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.edit' },
      },
      {
        path: 'runs',
        loadComponent: () =>
          import('./runs/bidding-runs-list-page.component').then(
            (m) => m.BiddingRunsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.runs' },
      },
      {
        path: 'runs/:runId',
        loadComponent: () =>
          import('./runs/bidding-run-detail-page.component').then(
            (m) => m.BiddingRunDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.run_detail' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/bid-decisions-list-page.component').then(
            (m) => m.BidDecisionsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.decisions' },
      },
      {
        path: 'decisions/:decisionId',
        loadComponent: () =>
          import('./decisions/bid-decision-detail-page.component').then(
            (m) => m.BidDecisionDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.decision' },
      },
      {
        path: 'actions',
        loadComponent: () =>
          import('./actions/bid-actions-list-page.component').then(
            (m) => m.BidActionsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.actions' },
      },
      {
        path: 'locks',
        loadComponent: () =>
          import('./locks/bid-locks-page.component').then(
            (m) => m.BidLocksPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.bidding.locks' },
      },
    ],
  },
];

export default routes;
