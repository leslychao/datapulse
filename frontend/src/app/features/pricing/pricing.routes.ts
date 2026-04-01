import { Routes } from '@angular/router';

const routes: Routes = [
  { path: '', redirectTo: 'policies', pathMatch: 'full' },
  {
    path: 'policies',
    loadComponent: () =>
      import('./pricing-layout.component').then((m) => m.PricingLayoutComponent),
    data: { breadcrumb: 'Политики' },
  },
];

export default routes;
