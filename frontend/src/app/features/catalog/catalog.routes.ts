import { Routes } from '@angular/router';
import { CatalogLayoutComponent } from './catalog-layout.component';

const routes: Routes = [
  {
    path: '',
    component: CatalogLayoutComponent,
    children: [
      { path: '', redirectTo: 'cost', pathMatch: 'full' },
      {
        path: 'cost',
        loadComponent: () =>
          import('./cost/catalog-cost-page.component').then(
            (m) => m.CatalogCostPageComponent,
          ),
        data: { breadcrumb: 'Себестоимость' },
      },
    ],
  },
];

export default routes;
