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
        data: { breadcrumb: 'Ценовые политики' },
      },
      {
        path: 'policies/new',
        canActivate: [pricingWriteGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./policies/policy-form-page.component').then(
            (m) => m.PolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Новая политика' },
      },
      {
        path: 'policies/:policyId/edit',
        canActivate: [pricingWriteGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./policies/policy-form-page.component').then(
            (m) => m.PolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Редактирование' },
      },
      {
        path: 'runs',
        loadComponent: () =>
          import('./runs/runs-list-page.component').then(
            (m) => m.RunsListPageComponent,
          ),
        data: { breadcrumb: 'Прогоны' },
      },
      {
        path: 'runs/:runId',
        loadComponent: () =>
          import('./runs/run-detail-page.component').then(
            (m) => m.RunDetailPageComponent,
          ),
        data: { breadcrumb: 'Детали прогона' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/decisions-list-page.component').then(
            (m) => m.DecisionsListPageComponent,
          ),
        data: { breadcrumb: 'Решения' },
      },
      {
        path: 'decisions/:decisionId',
        loadComponent: () =>
          import('./decisions/decision-detail-page.component').then(
            (m) => m.DecisionDetailPageComponent,
          ),
        data: { breadcrumb: 'Решение' },
      },
      {
        path: 'price-actions',
        loadComponent: () =>
          import('../execution/actions-list-page.component').then(
            (m) => m.ActionsListPageComponent,
          ),
        data: { breadcrumb: 'Применение цен' },
      },
      {
        path: 'price-actions/:actionId',
        loadComponent: () =>
          import('../execution/action-detail-page.component').then(
            (m) => m.ActionDetailPageComponent,
          ),
        data: { breadcrumb: 'Детали действия' },
      },
      {
        path: 'simulation',
        loadComponent: () =>
          import('../execution/simulation-page.component').then(
            (m) => m.SimulationPageComponent,
          ),
        data: { breadcrumb: 'Симуляция' },
      },
      {
        path: 'locks',
        loadComponent: () =>
          import('./locks/locks-page.component').then(
            (m) => m.LocksPageComponent,
          ),
        data: { breadcrumb: 'Блокировки' },
      },
      {
        path: 'competitors',
        loadComponent: () =>
          import('./competitors/competitors-page.component').then(
            (m) => m.CompetitorsPageComponent,
          ),
        data: { breadcrumb: 'Конкуренты' },
      },
      {
        path: 'insights',
        loadComponent: () =>
          import('./insights/insights-page.component').then(
            (m) => m.InsightsPageComponent,
          ),
        data: { breadcrumb: 'Инсайты' },
      },
    ],
  },
];

export default routes;
