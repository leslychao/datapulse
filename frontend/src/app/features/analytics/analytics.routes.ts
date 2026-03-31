import { Routes } from '@angular/router';

const routes: Routes = [
  { path: '', redirectTo: 'pnl', pathMatch: 'full' },
  {
    path: 'pnl',
    loadComponent: () =>
      import('./analytics-layout.component').then(
        (m) => m.AnalyticsLayoutComponent,
      ),
  },
];

export default routes;
