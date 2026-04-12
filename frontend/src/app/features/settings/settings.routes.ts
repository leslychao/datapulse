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
        data: { breadcrumb: 'breadcrumb.settings.profile' },
      },
      {
        path: 'connections',
        loadComponent: () =>
          import('./connections/connections-page.component').then((m) => m.ConnectionsPageComponent),
        data: { breadcrumb: 'breadcrumb.settings.connections' },
      },
      {
        path: 'connections/:connectionId',
        loadComponent: () =>
          import('./connection-detail/connection-detail-page.component').then(
            (m) => m.ConnectionDetailPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.settings.connection' },
      },
      {
        path: 'team',
        loadComponent: () =>
          import('./team/team-page.component').then((m) => m.TeamPageComponent),
        data: { breadcrumb: 'breadcrumb.settings.team' },
      },
      {
        path: 'invitations',
        loadComponent: () =>
          import('./invitations/invitations-page.component').then((m) => m.InvitationsPageComponent),
        data: { breadcrumb: 'breadcrumb.settings.invitations' },
      },
      {
        path: 'general',
        loadComponent: () =>
          import('./general/general-page.component').then((m) => m.GeneralPageComponent),
        data: { breadcrumb: 'breadcrumb.settings.general' },
      },
      {
        path: 'bidding',
        loadComponent: () =>
          import('./bidding/bidding-settings-page.component').then(
            (m) => m.BiddingSettingsPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.settings.bidding' },
      },
      {
        path: 'alert-rules',
        loadComponent: () =>
          import('./alert-rules/alert-rules-page.component').then(
            (m) => m.AlertRulesPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.settings.alert_rules' },
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./audit-log/audit-log-page.component').then(
            (m) => m.AuditLogPageComponent,
          ),
        data: { breadcrumb: 'breadcrumb.settings.audit_log' },
      },
    ],
  },
];

export default routes;
