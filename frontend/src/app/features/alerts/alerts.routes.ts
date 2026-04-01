import { Routes } from '@angular/router';

import { AlertsLayoutComponent } from './alerts-layout.component';

const routes: Routes = [
  {
    path: '',
    component: AlertsLayoutComponent,
    children: [
      { path: '', redirectTo: 'events', pathMatch: 'full' },
      {
        path: 'events',
        loadComponent: () =>
          import('./alert-events-page.component').then((m) => m.AlertEventsPageComponent),
        data: { breadcrumb: 'Алерты' },
      },
      {
        path: 'events/:eventId',
        loadComponent: () =>
          import('./alert-events-page.component').then((m) => m.AlertEventsPageComponent),
        data: { breadcrumb: 'Алерты' },
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications-page.component').then((m) => m.NotificationsPageComponent),
        data: { breadcrumb: 'Уведомления' },
      },
    ],
  },
];

export default routes;
