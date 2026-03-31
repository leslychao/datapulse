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
      },
      {
        path: 'connections/:connectionId',
        loadComponent: () =>
          import('./connection-detail/connection-detail-page.component').then(
            (m) => m.ConnectionDetailPageComponent,
          ),
      },
      {
        path: 'team',
        loadComponent: () =>
          import('./team/team-page.component').then((m) => m.TeamPageComponent),
      },
      {
        path: 'invitations',
        loadComponent: () =>
          import('./invitations/invitations-page.component').then((m) => m.InvitationsPageComponent),
      },
      {
        path: 'general',
        loadComponent: () =>
          import('./general/general-page.component').then((m) => m.GeneralPageComponent),
      },
    ],
  },
];

export default routes;
