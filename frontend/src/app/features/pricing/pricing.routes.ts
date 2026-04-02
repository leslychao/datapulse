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
        path: 'policies/:policyId/assignments',
        loadComponent: () =>
          import('./policies/policy-assignments-page.component').then(
            (m) => m.PolicyAssignmentsPageComponent,
          ),
        data: { breadcrumb: 'Назначения' },
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
        path: 'locks',
        loadComponent: () =>
          import('./locks/locks-page.component').then(
            (m) => m.LocksPageComponent,
          ),
        data: { breadcrumb: 'Блокировки' },
      },
    ],
  },
];

export default routes;
