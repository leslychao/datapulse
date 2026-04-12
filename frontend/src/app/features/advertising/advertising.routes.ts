import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'campaigns',
    pathMatch: 'full',
  },
  {
    path: 'campaigns',
    loadComponent: () =>
      import('./campaigns-page.component').then(
        (m) => m.CampaignsPageComponent,
      ),
    data: { breadcrumb: 'breadcrumb.advertising.campaigns' },
  },
];

export default routes;
