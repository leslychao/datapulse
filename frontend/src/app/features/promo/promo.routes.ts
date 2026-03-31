import { Routes } from '@angular/router';

const routes: Routes = [
  { path: '', redirectTo: 'campaigns', pathMatch: 'full' },
  {
    path: 'campaigns',
    loadComponent: () =>
      import('./promo-layout.component').then((m) => m.PromoLayoutComponent),
  },
];

export default routes;
