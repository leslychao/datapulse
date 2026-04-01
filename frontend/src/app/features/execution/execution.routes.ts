import { Routes } from '@angular/router';

const routes: Routes = [
  { path: '', redirectTo: 'actions', pathMatch: 'full' },
  {
    path: 'actions',
    loadComponent: () =>
      import('./actions-list-page.component').then(
        (m) => m.ActionsListPageComponent,
      ),
    data: { breadcrumb: 'Действия' },
  },
  {
    path: 'actions/:actionId',
    loadComponent: () =>
      import('./action-detail-page.component').then(
        (m) => m.ActionDetailPageComponent,
      ),
    data: { breadcrumb: 'Детали действия' },
  },
];

export default routes;
