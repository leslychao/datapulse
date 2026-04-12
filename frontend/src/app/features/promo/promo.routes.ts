import { Routes } from '@angular/router';

import { PromoLayoutComponent } from './promo-layout.component';
import { promoUnsavedChangesGuard } from './unsaved-changes.guard';
import { promoWriteGuard } from './promo-write.guard';

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
        data: { breadcrumb: 'breadcrumb.promo.campaigns' },
      },
      {
        path: 'campaigns/:campaignId',
        loadComponent: () =>
          import('./campaigns/campaign-detail-page.component').then(
            (m) => m.CampaignDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.promo.campaign_detail' },
      },
      {
        path: 'policies',
        loadComponent: () =>
          import('./policies/promo-policy-list-page.component').then(
            (m) => m.PromoPolicyListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.promo.policies' },
      },
      {
        path: 'policies/new',
        loadComponent: () =>
          import('./policies/promo-policy-form-page.component').then(
            (m) => m.PromoPolicyFormPageComponent,
          ),
        canActivate: [promoWriteGuard],
        canDeactivate: [promoUnsavedChangesGuard],
        data: { breadcrumb: 'breadcrumb.promo.new_policy' },
      },
      {
        path: 'policies/:policyId/edit',
        loadComponent: () =>
          import('./policies/promo-policy-form-page.component').then(
            (m) => m.PromoPolicyFormPageComponent,
          ),
        canActivate: [promoWriteGuard],
        canDeactivate: [promoUnsavedChangesGuard],
        data: { breadcrumb: 'breadcrumb.promo.edit' },
      },
      {
        path: 'policies/:policyId/assignments',
        loadComponent: () =>
          import('./policies/promo-policy-assignments-page.component').then(
            (m) => m.PromoPolicyAssignmentsPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.promo.assignments' },
      },
      {
        path: 'evaluations',
        loadComponent: () =>
          import('./evaluations/evaluations-list-page.component').then(
            (m) => m.EvaluationsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.promo.evaluations' },
      },
      {
        path: 'decisions',
        loadComponent: () =>
          import('./decisions/decisions-list-page.component').then(
            (m) => m.DecisionsListPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.promo.decisions' },
      },
    ],
  },
];

export default routes;
