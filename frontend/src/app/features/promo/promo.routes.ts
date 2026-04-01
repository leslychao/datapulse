import { Routes } from '@angular/router';

import { PromoLayoutComponent } from './promo-layout.component';

const routes: Routes = [
  {
    path: '',
    component: PromoLayoutComponent,
    children: [
      { path: '', redirectTo: 'campaigns', pathMatch: 'full' },
      {
        path: 'campaigns',
        loadComponent: () =>
          import('./campaigns/campaign-list-page.component').then(
            (m) => m.CampaignListPageComponent,
          ),
        data: { breadcrumb: 'Кампании' },
      },
      {
        path: 'campaigns/:campaignId',
        loadComponent: () =>
          import('./campaigns/campaign-detail-page.component').then(
            (m) => m.CampaignDetailPageComponent,
          ),
        data: { breadcrumb: 'Детали кампании' },
      },
      {
        path: 'policies',
        loadComponent: () =>
          import('./policies/promo-policy-list-page.component').then(
            (m) => m.PromoPolicyListPageComponent,
          ),
        data: { breadcrumb: 'Промо-политики' },
      },
      {
        path: 'policies/new',
        loadComponent: () =>
          import('./policies/promo-policy-form-page.component').then(
            (m) => m.PromoPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Новая политика' },
      },
      {
        path: 'policies/:policyId',
        loadComponent: () =>
          import('./policies/promo-policy-form-page.component').then(
            (m) => m.PromoPolicyFormPageComponent,
          ),
        data: { breadcrumb: 'Редактирование' },
      },
      {
        path: 'policies/:policyId/assignments',
        loadComponent: () =>
          import('./policies/promo-policy-assignments-page.component').then(
            (m) => m.PromoPolicyAssignmentsPageComponent,
          ),
        data: { breadcrumb: 'Назначения' },
      },
      {
        path: 'evaluations',
        loadComponent: () =>
          import('./evaluations/evaluations-list-page.component').then(
            (m) => m.EvaluationsListPageComponent,
          ),
        data: { breadcrumb: 'Оценки' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/decisions-list-page.component').then(
            (m) => m.DecisionsListPageComponent,
          ),
        data: { breadcrumb: 'Решения' },
      },
    ],
  },
];

export default routes;
