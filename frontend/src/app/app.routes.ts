import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { rootRedirectGuard } from './core/auth/root-redirect.guard';
import { onboardingGuard } from './core/auth/onboarding.guard';
import { workspaceGuard } from './core/auth/workspace.guard';

const loadCallbackComponent = () =>
  import('./features/callback/callback.component').then((m) => m.CallbackComponent);

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [rootRedirectGuard],
    loadComponent: loadCallbackComponent,
  },
  {
    path: 'callback',
    loadComponent: loadCallbackComponent,
  },
  {
    path: 'workspaces',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/workspace-selector/workspace-selector.component').then(
        (m) => m.WorkspaceSelectorComponent,
      ),
  },
  {
    path: 'onboarding',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () =>
      import('./features/onboarding/onboarding-wizard.component').then(
        (m) => m.OnboardingWizardComponent,
      ),
  },
  {
    path: 'invitation/accept',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/invitation/invitation-accept.component').then(
        (m) => m.InvitationAcceptComponent,
      ),
  },
  {
    path: 'workspace/:workspaceId',
    canActivate: [authGuard, workspaceGuard],
    loadComponent: () =>
      import('./shared/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'grid', pathMatch: 'full' },
      {
        path: 'grid',
        loadChildren: () => import('./features/grid/grid.routes'),
        data: { breadcrumb: 'Операции' },
      },
      {
        path: 'analytics',
        loadChildren: () => import('./features/analytics/analytics.routes'),
        data: { breadcrumb: 'Аналитика' },
      },
      {
        path: 'pricing',
        loadChildren: () => import('./features/pricing/pricing.routes'),
        data: { breadcrumb: 'Ценообразование' },
      },
      {
        path: 'promo',
        loadChildren: () => import('./features/promo/promo.routes'),
        data: { breadcrumb: 'Промо' },
      },
      {
        path: 'execution/actions/:actionId',
        redirectTo: 'pricing/price-actions/:actionId',
        pathMatch: 'full',
      },
      {
        path: 'execution/actions',
        redirectTo: 'pricing/price-actions',
        pathMatch: 'full',
      },
      {
        path: 'execution/simulation',
        redirectTo: 'pricing/simulation',
        pathMatch: 'full',
      },
      {
        path: 'execution',
        redirectTo: 'pricing/price-actions',
        pathMatch: 'full',
      },
      {
        path: 'mismatches',
        loadChildren: () => import('./features/mismatches/mismatches.routes'),
        data: { breadcrumb: 'Расхождения' },
      },
      {
        path: 'queues',
        loadChildren: () => import('./features/queues/queues.routes'),
        data: { breadcrumb: 'Очереди' },
      },
      {
        path: 'alerts',
        loadChildren: () => import('./features/alerts/alerts.routes'),
        data: { breadcrumb: 'Алерты' },
      },
      {
        path: 'settings',
        loadChildren: () => import('./features/settings/settings.routes'),
        data: { breadcrumb: 'Настройки' },
      },
    ],
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        (m) => m.NotFoundComponent,
      ),
  },
];
