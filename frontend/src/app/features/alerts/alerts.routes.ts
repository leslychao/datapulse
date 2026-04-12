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
        data: { breadcrumb: 'breadcrumb.alerts.events' },
      },
      {
        path: 'events/:eventId',
        loadComponent: () =>
          import('./alert-events-page.component').then((m) => m.AlertEventsPageComponent),
        data: { breadcrumb: 'breadcrumb.alerts.events' },
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications-page.component').then((m) => m.NotificationsPageComponent),
        data: { breadcrumb: 'breadcrumb.alerts.notifications' },
      },
    ],
  },
];

export default routes;
