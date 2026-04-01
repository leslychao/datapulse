import { Routes } from '@angular/router';
import { SettingsLayoutComponent } from './settings-layout.component';

const routes: Routes = [
  {
    path: '',
    component: SettingsLayoutComponent,
    children: [
      { path: '', redirectTo: 'connections', pathMatch: 'full' },
      {
        path: 'connections',
        loadComponent: () =>
          import('./connections/connections-page.component').then((m) => m.ConnectionsPageComponent),
        data: { breadcrumb: 'Подключения' },
      },
      {
        path: 'connections/:connectionId',
        loadComponent: () =>
          import('./connection-detail/connection-detail-page.component').then(
            (m) => m.ConnectionDetailPageComponent,
          ),
        data: { breadcrumb: 'Подключение' },
      },
      {
        path: 'team',
        loadComponent: () =>
          import('./team/team-page.component').then((m) => m.TeamPageComponent),
        data: { breadcrumb: 'Команда' },
      },
      {
        path: 'invitations',
        loadComponent: () =>
          import('./invitations/invitations-page.component').then((m) => m.InvitationsPageComponent),
        data: { breadcrumb: 'Приглашения' },
      },
      {
        path: 'general',
        loadComponent: () =>
          import('./general/general-page.component').then((m) => m.GeneralPageComponent),
        data: { breadcrumb: 'Общие' },
      },
    ],
  },
];

export default routes;
