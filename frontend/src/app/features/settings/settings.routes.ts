import { Routes } from '@angular/router';

const routes: Routes = [
  { path: '', redirectTo: 'connections', pathMatch: 'full' },
  {
    path: 'connections',
    loadComponent: () =>
      import('./settings-layout.component').then((m) => m.SettingsLayoutComponent),
  },
];

export default routes;
