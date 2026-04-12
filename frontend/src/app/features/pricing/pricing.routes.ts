import { Routes } from '@angular/router';

import { PricingLayoutComponent } from './pricing-layout.component';
import { pricingWriteGuard } from './pricing-write.guard';
import { unsavedChangesGuard } from './unsaved-changes.guard';

const routes: Routes = [
  {
    path: '',
    component: PricingLayoutComponent,
    children: [
      { path: '', redirectTo: 'policies', pathMatch: 'full' },
      {
        path: 'policies',
        loadComponent: () =>
          import('./policies/policy-list-page.component').then(
            (m) => m.PolicyListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.policies' },
      },
      {
        path: 'policies/new',
        canActivate: [pricingWriteGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./policies/policy-form-page.component').then(
            (m) => m.PolicyFormPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.new_policy' },
      },
      {
        path: 'policies/:policyId/edit',
        canActivate: [pricingWriteGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./policies/policy-form-page.component').then(
            (m) => m.PolicyFormPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.edit' },
      },
      {
        path: 'runs',
        loadComponent: () =>
          import('./runs/runs-list-page.component').then(
            (m) => m.RunsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.runs' },
      },
      {
        path: 'runs/:runId',
        loadComponent: () =>
          import('./runs/run-detail-page.component').then(
            (m) => m.RunDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.run_detail' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/decisions-list-page.component').then(
            (m) => m.DecisionsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.decisions' },
      },
      {
        path: 'decisions/:decisionId',
        loadComponent: () =>
          import('./decisions/decision-detail-page.component').then(
            (m) => m.DecisionDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.decision' },
      },
      {
        path: 'price-actions',
        loadComponent: () =>
          import('../execution/actions-list-page.component').then(
            (m) => m.ActionsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.price_actions' },
      },
      {
        path: 'price-actions/:actionId',
        loadComponent: () =>
          import('../execution/action-detail-page.component').then(
            (m) => m.ActionDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.action_detail' },
      },
      {
        path: 'simulation',
        loadComponent: () =>
          import('../execution/simulation-page.component').then(
            (m) => m.SimulationPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.simulation' },
      },
      {
        path: 'locks',
        loadComponent: () =>
          import('./locks/locks-page.component').then(
            (m) => m.LocksPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.locks' },
      },
      {
        path: 'competitors',
        loadComponent: () =>
          import('./competitors/competitors-page.component').then(
            (m) => m.CompetitorsPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.competitors' },
      },
      {
        path: 'insights',
        loadComponent: () =>
          import('./insights/insights-page.component').then(
            (m) => m.InsightsPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.pricing.insights' },
      },
    ],
  },
];

export default routes;
