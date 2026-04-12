import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./mismatch-dashboard-page.component').then(
        (m) => m.MismatchDashboardPageComponent,
      ),
    data: { breadcrumb: 'breadcrumb.mismatches.list' },
  },
  {
    path: ':mismatchId',
    loadComponent: () =>
      import('./mismatch-detail-page.component').then(
        (m) => m.MismatchDetailPageComponent,
      ),
    data: { breadcrumb: 'breadcrumb.mismatches.detail' },
  },
];

export default routes;
