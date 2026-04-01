import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./mismatch-dashboard-page.component').then(
        (m) => m.MismatchDashboardPageComponent,
      ),
    data: { breadcrumb: 'Расхождения' },
  },
];

export default routes;
