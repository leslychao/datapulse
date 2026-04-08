import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./grid-page.component').then((m) => m.GridPageComponent),
  },
  {
    path: 'offer/:offerId',
    loadComponent: () =>
      import('./offer-detail-page.component').then(
        (m) => m.OfferDetailPageComponent,
      ),
    data: { breadcrumb: 'Детали товара' },
  },
];

export default routes;
