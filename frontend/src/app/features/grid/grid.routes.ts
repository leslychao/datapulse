import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./grid-page.component').then((m) => m.GridPageComponent),
  },
];

export default routes;
