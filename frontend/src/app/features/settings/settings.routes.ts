import { Routes } from '@angular/router';
import { SettingsLayoutComponent } from './settings-layout.component';

const routes: Routes = [
  {
    path: '',
    component: SettingsLayoutComponent,
    children: [
      { path: '', redirectTo: 'general', pathMatch: 'full' },
      {
        path: 'profile',
        loadComponent: () =>
          import('./profile/profile-page.component').then((m) => m.ProfilePageComponent),
        data: { breadcrumb: 'Профиль' },
      },
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
      {
        path: 'cost-profiles',
        loadComponent: () =>
          import('./cost-profiles/cost-profiles-page.component').then(
            (m) => m.CostProfilesPageComponent,
          ),
        data: { breadcrumb: 'Себестоимость' },
      },
      {
        path: 'alert-rules',
        loadComponent: () =>
          import('./alert-rules/alert-rules-page.component').then(
            (m) => m.AlertRulesPageComponent,
          ),
        data: { breadcrumb: 'Правила алертов' },
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./audit-log/audit-log-page.component').then(
            (m) => m.AuditLogPageComponent,
          ),
        data: { breadcrumb: 'Журнал аудита' },
      },
    ],
  },
];

export default routes;
