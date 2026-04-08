# Frontend Architecture

Technical architecture document describing the current state of the Datapulse Angular frontend.
For design specifications and page layouts, see `pages-*.md` and `frontend-design-direction.md`.

---

## 1. Overview

Datapulse frontend is a **single-page application** (SPA) built with Angular 19 in standalone-component mode.
The application follows a Cursor IDE-inspired design — desktop-first, dense, professional, light theme only (MVP).

### Core stack

| Layer | Technology | Version |
|---|---|---|
| Framework | Angular (standalone components, signals) | 19.x |
| Language | TypeScript (strict mode) | 5.6 |
| Build toolchain | Angular CLI (`@angular/build`, esbuild-based) | 19.x |
| Styling | Tailwind CSS + CSS custom properties | 4.x |
| Server state | TanStack Query (`@tanstack/angular-query-experimental`) | 5.x |
| UI state | NgRx Signal Store (`@ngrx/signals`) | 19.x |
| Data grid | AG Grid Community (`ag-grid-angular`) | 32.x |
| Icons | Lucide Angular (`lucide-angular`) | 1.x |
| i18n | `@ngx-translate/core` + `@ngx-translate/http-loader` | 16.x |
| WebSocket | `@stomp/rx-stomp` (STOMP over WebSocket) | 2.x |
| Date utilities | date-fns | 4.x |
| CDK | `@angular/cdk` (overlay, drag-drop, a11y) | 19.x |
| RxJS | rxjs | 7.8 |

---

## 2. Project Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── core/                          # Singletons: API transport, auth, models, websocket
│   │   │   ├── api/                       # HTTP services (one per backend resource)
│   │   │   │   ├── connection-api.service.ts
│   │   │   │   ├── workspace-api.service.ts
│   │   │   │   ├── member-api.service.ts
│   │   │   │   ├── invitation-api.service.ts
│   │   │   │   ├── notification-api.service.ts
│   │   │   │   ├── search-api.service.ts
│   │   │   │   └── user-api.service.ts
│   │   │   ├── auth/                      # Authentication: service, guards, interceptor
│   │   │   │   ├── auth.service.ts
│   │   │   │   ├── auth.guard.ts
│   │   │   │   ├── auth.interceptor.ts
│   │   │   │   ├── root-redirect.guard.ts
│   │   │   │   ├── onboarding.guard.ts
│   │   │   │   └── workspace.guard.ts
│   │   │   ├── models/                    # TypeScript interfaces and type aliases
│   │   │   │   ├── index.ts              # Barrel re-export
│   │   │   │   ├── user.model.ts
│   │   │   │   ├── workspace.model.ts
│   │   │   │   ├── connection.model.ts
│   │   │   │   ├── member.model.ts
│   │   │   │   ├── invitation.model.ts
│   │   │   │   ├── notification.model.ts
│   │   │   │   └── search.model.ts
│   │   │   └── websocket/
│   │   │       └── websocket.service.ts
│   │   │
│   │   ├── shared/                        # Reusable across features
│   │   │   ├── components/                # Atomic UI components
│   │   │   │   ├── status-badge.component.ts
│   │   │   │   ├── marketplace-badge.component.ts
│   │   │   │   ├── section-card.component.ts
│   │   │   │   └── confirmation-modal.component.ts
│   │   │   ├── stores/                    # NgRx Signal Stores (global UI state)
│   │   │   │   ├── workspace-context.store.ts
│   │   │   │   ├── notification.store.ts
│   │   │   │   ├── tab.store.ts
│   │   │   │   └── sync-status.store.ts
│   │   │   ├── services/                  # UI coordination services
│   │   │   │   ├── detail-panel.service.ts
│   │   │   │   ├── shortcut.service.ts
│   │   │   │   └── breadcrumb.service.ts
│   │   │   ├── shell/                     # Application shell (IDE-like frame)
│   │   │   │   ├── shell.component.ts
│   │   │   │   ├── top-bar/              # Workspace switcher, breadcrumbs, search, user menu
│   │   │   │   ├── activity-bar/         # Left icon navigation bar
│   │   │   │   ├── tab-bar/              # Editor-style tabs
│   │   │   │   ├── detail-panel/         # Right slide-in panel
│   │   │   │   ├── bottom-panel/         # Collapsible bottom area
│   │   │   │   ├── status-bar/           # Bottom status strip
│   │   │   │   ├── command-palette/      # Ctrl+K search overlay
│   │   │   │   └── toast/               # Toast notification system
│   │   │   ├── layout/                    # Layout primitives
│   │   │   │   ├── viewport-guard.component.ts
│   │   │   │   ├── centered-content.component.ts
│   │   │   │   ├── minimal-top-bar.component.ts
│   │   │   │   ├── spinner.component.ts
│   │   │   │   └── status-message.component.ts
│   │   │   └── pipes/                     # Shared pure pipes
│   │   │
│   │   ├── features/                      # Business features (lazy-loaded)
│   │   │   ├── grid/                      # Operational grid (main workspace view)
│   │   │   ├── analytics/                 # P&L and analytics dashboards
│   │   │   ├── pricing/                   # Pricing policies management
│   │   │   ├── promo/                     # Promo campaign management
│   │   │   ├── settings/                  # Settings (connections, team, invitations, general)
│   │   │   │   ├── settings.routes.ts
│   │   │   │   ├── settings-layout.component.ts
│   │   │   │   ├── connections/
│   │   │   │   ├── connection-detail/
│   │   │   │   ├── team/
│   │   │   │   ├── invitations/
│   │   │   │   └── general/
│   │   │   ├── onboarding/               # First-run wizard
│   │   │   ├── workspace-selector/       # Multi-workspace chooser
│   │   │   ├── callback/                 # OAuth2 callback handler
│   │   │   ├── invitation/               # Invitation accept flow
│   │   │   └── not-found/                # 404 page
│   │   │
│   │   ├── app.component.ts              # Root: router-outlet + viewport guard
│   │   ├── app.config.ts                 # ApplicationConfig (providers)
│   │   └── app.routes.ts                 # Root route definitions
│   │
│   ├── environments/
│   │   ├── environment.ts                # Development config
│   │   └── environment.prod.ts           # Production config
│   │
│   ├── locale/
│   │   └── ru.json                       # Russian translations
│   │
│   ├── styles.css                        # Global styles: Tailwind import, CSS tokens, scrollbar, focus ring
│   ├── index.html
│   └── main.ts                           # Bootstrap entry point
│
├── angular.json                          # Angular CLI config (prefix: dp, builder: @angular/build)
├── tsconfig.json                         # TypeScript config (strict, path aliases)
├── package.json
└── proxy.conf.cjs                        # Dev proxy: /api,/ws,/oauth2,/auth → NG_PROXY_TARGET / PUBLIC_EDGE_URL
```

### Dependency direction

```
features/ ──→ shared/ ──→ core/
                ↑
            core/ does NOT depend on shared/ or features/
```

Cross-feature imports are prohibited. Shared logic goes into `shared/`.

### Path aliases

Defined in `tsconfig.json`:

| Alias | Maps to |
|---|---|
| `@core/*` | `src/app/core/*` |
| `@shared/*` | `src/app/shared/*` |
| `@features/*` | `src/app/features/*` |
| `@env` | `src/environments/environment` |

---

## 3. Bootstrap & Configuration

### Entry point

`main.ts` calls `bootstrapApplication(AppComponent, appConfig)` — no NgModules.

### ApplicationConfig (`app.config.ts`)

Providers registered at bootstrap:

| Provider | Purpose |
|---|---|
| `provideRouter(routes, withComponentInputBinding())` | Router with automatic route param → input binding |
| `provideHttpClient(withInterceptors([authInterceptor]))` | HTTP client with auth interceptor |
| `provideAnimations()` | Angular animations support |
| `provideAngularQuery(new QueryClient({...}))` | TanStack Query with 30s stale time, 1 retry |
| `importProvidersFrom(TranslateModule.forRoot({...}))` | ngx-translate with Russian default, HTTP loader from `/locale/` |

### Environment config

```typescript
// environment.ts (dev) / environment.prod.ts (prod)
export const environment = {
  production: false,
  apiUrl: '/api',           // proxied to backend in dev
  wsUrl: '/ws',             // WebSocket endpoint
  oauth2: {
    loginUrl: '/oauth2/start',
    logoutUrl: '/oauth2/sign_out',
  },
};
```

### Dev proxy (`proxy.conf.cjs`)

| Path | Target | Notes |
|---|---|---|
| `/api` | `NG_PROXY_TARGET` or `PUBLIC_EDGE_URL` | REST API via edge nginx/oauth2-proxy |
| `/ws` | `NG_PROXY_TARGET` or `PUBLIC_EDGE_URL` | WebSocket (STOMP) via edge nginx |
| `/oauth2` | `NG_PROXY_TARGET` or `PUBLIC_EDGE_URL` | OAuth2 proxy endpoints (`/oauth2/start`, `/oauth2/callback`) |
| `/auth` | `NG_PROXY_TARGET` or `PUBLIC_EDGE_URL` | Keycloak browser endpoints (local dev) |

Production: nginx handles the same routing (see `infra/nginx/prod.conf`).

---

## 4. Routing Architecture

### Root routes (`app.routes.ts`)

```
/                              → rootRedirectGuard → smart redirect
/callback                      → OAuth2 callback
/workspaces                    → [authGuard] Workspace selector
/onboarding                    → [authGuard, onboardingGuard] First-run wizard
/invitations/accept            → [authGuard] Invitation accept
/workspace/:workspaceId        → [authGuard, workspaceGuard] Shell (main app frame)
  /grid                        → Operational grid (default)
  /analytics                   → P&L and analytics
  /pricing                     → Pricing policies
  /promo                       → Promo campaigns
  /settings                    → Settings (sub-routes: connections, team, invitations, general)
/**                            → 404 Not Found
```

### Lazy loading strategy

All feature modules are lazy-loaded:

- **`loadComponent`** — single-page features (workspace selector, onboarding, callback, 404)
- **`loadChildren`** — multi-page features with sub-routes (settings, grid, analytics, pricing, promo)

Feature route files export `default routes` (default export for Angular lazy loading):

```typescript
const routes: Routes = [ ... ];
export default routes;
```

### Breadcrumbs

Routes carry `data: { breadcrumb: 'Label' }` — consumed by `BreadcrumbService` → `BreadcrumbsComponent` in Top Bar.

### Guard chain

| Guard | Purpose | Applied to |
|---|---|---|
| `rootRedirectGuard` | Smart redirect: onboarding / single workspace / last workspace / selector | `/` only |
| `authGuard` | Session check → redirect to OAuth2 login if not authenticated | All protected routes |
| `onboardingGuard` | Redirect to `/workspaces` if user already has workspaces | `/onboarding` |
| `workspaceGuard` | Verify user has membership in target workspace | `/workspace/:id` |

All guards are **functional** (`CanActivateFn`), not class-based.

---

## 5. Authentication Flow

```
User opens app
    │
    ▼
rootRedirectGuard
    │
    ├── Not authenticated? ──→ AuthService.login() ──→ Redirect to /oauth2/start (Keycloak)
    │                                                       │
    │                                                       ▼
    │                                                  Keycloak login page
    │                                                       │
    │                                                       ▼
    │                                                  /callback ──→ session cookie set by oauth2-proxy
    │                                                       │
    │                                                       ▼
    │                                              AuthService.checkSession()
    │                                              GET /api/users/me
    │                                                       │
    │                                                       ▼
    │                                              UserProfile loaded into signal
    │
    ├── Needs onboarding? ──→ /onboarding
    ├── Single workspace? ──→ /workspace/:id/grid
    ├── Last workspace in localStorage? ──→ /workspace/:lastId/grid
    └── Multiple workspaces ──→ /workspaces (selector)
```

### Session management

- **No tokens in frontend** — session managed by oauth2-proxy via HTTP-only cookie.
- `AuthService` stores `UserProfile` in a writable signal (`_user`), exposes readonly `user()`.
- `sessionChecked` signal tracks whether initial `/api/users/me` call has completed.
- `isAuthenticated` is a computed signal derived from `_user`.

### Interceptor (`authInterceptor`)

- Adds `X-Workspace-Id` header to all `/api/*` requests (except skip-list: `/api/users/me`, `/api/tenants`, `/api/workspaces`, `/api/invitations/accept`).
- Catches HTTP 401 → triggers `AuthService.login()` redirect.
- Gets workspace ID from `WorkspaceContextStore.currentWorkspaceId()`.

### Logout

`AuthService.logout()` redirects to `/oauth2/sign_out?rd=/`. Preserves last workspace ID in localStorage for next login.

---

## 6. State Management Strategy

Three tiers of state management, each for a specific purpose:

### Tier 1: TanStack Query — Server state

For any data fetched from the backend API. Provides caching, background refresh, loading/error states, and stale detection.

**Used in:** feature page components.

```typescript
readonly connectionsQuery = injectQuery(() => ({
  queryKey: ['connections'],
  queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
}));
```

Global defaults (set in `appConfig`):
- `staleTime: 30_000` — data considered fresh for 30 seconds
- `retry: 1` — one retry on failure

Mutations use `injectMutation()` with mandatory `onSuccess` (refetch + toast) and `onError` (toast).

### Tier 2: NgRx Signal Store — Global UI state

For state shared across multiple components that is not tied to a specific API request.

| Store | State | Persistence |
|---|---|---|
| `WorkspaceContextStore` | Current workspace ID and name | localStorage (`dp_last_workspace_id`) |
| `NotificationStore` | Notification list, unread count | In-memory (hydrated from WebSocket + REST) |
| `TabStore` | Open tabs per module, active tab | sessionStorage (`dp:tabs:{wsId}:{module}`) |
| `SyncStatusStore` | Connection sync health per connection | In-memory (updated via WebSocket) |

All stores are `{ providedIn: 'root' }` singletons using `signalStore()` with `withState`, `withComputed`, `withMethods`.

### Tier 3: Angular signals — Local component state

For component-private UI state: form step, selected item, modal visibility.

```typescript
readonly formStep = signal<FormStep>('idle');
readonly showRemoveModal = signal(false);
```

### Decision matrix

| Data type | Tool | Why |
|---|---|---|
| API data (lists, details) | TanStack Query | Cache, refetch, loading/error |
| Current workspace | Signal Store | Shared, persisted |
| Tabs | Signal Store | Shared, persisted |
| Notifications | Signal Store | WebSocket-driven, shared |
| Form step / local toggle | `signal()` | Component-private |
| Derived value | `computed()` | Reactive derivation |

---

## 7. Data Flow

### HTTP (REST API)

```
Component                   API Service              Backend
    │                           │                       │
    │  injectQuery(queryFn)     │                       │
    │──────────────────────────►│  http.get<T>(url)     │
    │                           │──────────────────────►│
    │                           │◄──────────────────────│
    │  query result (signal)    │                       │
    │◄──────────────────────────│                       │
    │                           │                       │
    │  injectMutation(mutFn)    │                       │
    │──────────────────────────►│  http.post<T>(url, body)
    │                           │──────────────────────►│
    │                           │◄──────────────────────│
    │  onSuccess → refetch      │                       │
    │  onError → toast          │                       │
```

### WebSocket (STOMP)

```
Backend (Spring STOMP)                WebSocketService                 Stores
    │                                       │                           │
    │  /topic/workspace/{id}/sync-status    │                           │
    │  (WorkspaceSyncStatusPush)              │                           │
    │──────────────────────────────────────►│  subscribeTo()            │
    │                                       │──────────────────────────►│ SyncStatusStore
    │                                       │                           │ .upsertConnection()
    │  /user/queue/notifications            │                           │
    │──────────────────────────────────────►│                           │
    │                                       │──────────────────────────►│ NotificationStore
    │                                       │                           │ .addNotification()
```

**WebSocket topics subscribed on workspace entry:**
- `/topic/workspace/{id}/alerts` — alert events (TODO: Phase B)
- `/topic/workspace/{id}/sync-status` — `WorkspaceSyncStatusPush` (health DTO + `reason`; bootstrap `GET /api/connections/sync-health`)
- `/topic/workspace/{id}/actions` — action lifecycle events (TODO: Phase D)
- `/user/queue/notifications` — personal notifications

**Reconnection:** exponential backoff (1s → 30s max). On reconnect, missed notifications are synced via REST (`NotificationApiService.list({ since })`).

---

## 8. API Layer

### Transport pattern

API services are pure HTTP transport — no business logic, no state, no error handling.

```typescript
@Injectable({ providedIn: 'root' })
export class ConnectionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listConnections(): Observable<ConnectionSummary[]> {
    return this.http.get<ConnectionSummary[]>(`${this.base}/connections`);
  }
}
```

Rules:
- Returns `Observable<T>` — consumed by TanStack Query via `lastValueFrom()`.
- No `catchError`, `tap`, or side effects.
- URL composed as `${this.base}/resource/${id}/sub-resource`.
- Query parameters via `HttpParams`.
- One service per backend resource: `ConnectionApiService`, `MemberApiService`, `WorkspaceApiService`, etc.

### Current API services

| Service | Backend resource |
|---|---|
| `ConnectionApiService` | `/api/connections` — CRUD, validate, enable/disable, sync, call-log |
| `WorkspaceApiService` | `/api/workspaces`, `/api/tenants` — workspace/tenant management |
| `MemberApiService` | `/api/workspaces/{id}/members` — team members, roles |
| `InvitationApiService` | `/api/invitations` — send, accept, list |
| `NotificationApiService` | `/api/notifications` — list, mark read, unread count |
| `SearchApiService` | `/api/search` — global search (Ctrl+K) |
| `UserApiService` | `/api/users/me` — current user profile |

---

## 9. Shell Architecture

The application shell follows a Cursor IDE-inspired layout with five persistent zones:

```
┌─────────────────────────────────────────────────────────────┐
│  Top Bar (40px): workspace switcher · breadcrumbs · search · user │
├──────┬──────────────────────────────────────┬───────────────┤
│      │  Tab Bar (36px)                      │               │
│  A   ├──────────────────────────────────────┤   Detail      │
│  c   │                                      │   Panel       │
│  t   │           Main Area                  │   (right)     │
│  i   │           <router-outlet />          │               │
│  v   │                                      │   slide-in,   │
│  i   │                                      │   resizable   │
│  t   │                                      │               │
│  y   ├──────────────────────────────────────┤               │
│      │  Bottom Panel (collapsible)          │               │
│ Bar  │                                      │               │
├──────┴──────────────────────────────────────┴───────────────┤
│  Status Bar (24px): sync health · connection status          │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

`ShellComponent` uses CSS Grid with named areas:

```
grid-template-columns: 48px 1fr [detail-width]px
grid-template-rows: 40px 36px 1fr auto 24px
grid-template-areas:
  'topbar  topbar  topbar'
  'actbar  tabbar  detail'
  'actbar  main    detail'
  'actbar  bottom  detail'
  'status  status  status'
```

Detail panel width is dynamic — driven by `DetailPanelService.width()` signal. When closed, column collapses to 0px.

### Zone components

| Zone | Component | Purpose |
|---|---|---|
| Top Bar | `TopBarComponent` | Workspace switcher, breadcrumbs, search trigger, notification bell, user menu |
| Activity Bar | `ActivityBarComponent` | Module navigation icons (48px wide) |
| Tab Bar | `TabBarComponent` | Editor-style tabs per module |
| Main Area | `<router-outlet />` | Current feature view |
| Detail Panel | `DetailPanelComponent` | Contextual entity detail (right side) |
| Bottom Panel | `BottomPanelComponent` | Bulk actions, notifications (collapsible) |
| Status Bar | `StatusBarComponent` | Sync health indicators |
| Overlays | `CommandPaletteComponent`, `ToastContainerComponent` | Ctrl+K palette, toast notifications |

### Shell lifecycle

On `ShellComponent.ngOnInit()`:
1. Extract `workspaceId` from route params.
2. Set workspace context in `WorkspaceContextStore`.
3. Connect WebSocket and subscribe to workspace topics.
4. Initialize keyboard shortcuts.

On `ShellComponent.ngOnDestroy()`: disconnect WebSocket.

---

## 10. Styling

### Tailwind CSS 4

Global import in `styles.css`:

```css
@import "tailwindcss";
```

Components use Tailwind utility classes with CSS variable arbitrary values:

```html
<span class="text-[var(--text-primary)] bg-[var(--bg-secondary)] rounded-[var(--radius-md)]">
```

### Design tokens (CSS custom properties)

All tokens defined on `:root` in `styles.css`:

| Category | Tokens |
|---|---|
| Background | `--bg-primary` (#FFF), `--bg-secondary` (#F9FAFB), `--bg-tertiary` (#F3F4F6), `--bg-active` (#EFF6FF) |
| Border | `--border-default` (#E5E7EB), `--border-subtle` (#F3F4F6) |
| Text | `--text-primary` (#111827), `--text-secondary` (#6B7280), `--text-tertiary` (#9CA3AF) |
| Accent | `--accent-primary` (#2563EB), `--accent-primary-hover` (#1D4ED8), `--accent-subtle` (#EFF6FF) |
| Status | `--status-success`, `--status-warning`, `--status-error`, `--status-info`, `--status-neutral` |
| Finance | `--finance-positive`, `--finance-negative`, `--finance-zero` |
| Typography | `--text-xs` (11px) → `--text-2xl` (24px) |
| Radius | `--radius-sm` (4px), `--radius-md` (6px), `--radius-lg` (8px) |
| Shadow | `--shadow-sm`, `--shadow-md` |
| Transition | `--transition-fast` (150ms), `--transition-normal` (200ms) |

### Fonts

| Role | Font | Fallback |
|---|---|---|
| UI chrome | Inter | system-ui, -apple-system, sans-serif |
| Data / numbers | JetBrains Mono (`.font-mono`) | Cascadia Code, Fira Code, monospace |

### No component-level CSS files

Styling is done exclusively through Tailwind classes and CSS variables. Component `styles` array is only used for `@keyframes` animations. No SCSS, no CSS modules.

### Dark theme

Not implemented (MVP). Token structure on `:root` allows future dark theme via token-swap without component changes.

---

## 11. i18n

### Architecture

- Library: `@ngx-translate/core` with `TranslateHttpLoader`.
- Default language: Russian (`ru`).
- Translation file: `frontend/src/locale/ru.json` — loaded via HTTP at app startup.
- Keys: dot-separated English strings → Russian values.

### Usage

```html
{{ 'settings.connections.title' | translate }}
{{ 'pricing.guard.stale_data' | translate:{ hours: 24 } }}
```

### Backend contract

Backend sends `messageKey` (not human-readable text) in REST responses. Frontend resolves the key via `translate` pipe.

### Current state

Some UI strings are still hardcoded in templates (especially Settings pages). These should be migrated to `ru.json` over time.

---

## 12. Implementation Status

| Area | Status | Notes |
|---|---|---|
| **Bootstrap & config** | Implemented | `app.config.ts`, environment, proxy |
| **Auth flow** | Implemented | `AuthService`, guards, interceptor, OAuth2 redirect |
| **Shell layout** | Implemented | All 6 zones, CSS Grid, responsive detail panel |
| **Workspace management** | Implemented | Selector, switcher, context store |
| **Onboarding wizard** | Implemented | Tenant + workspace creation flow |
| **Settings: Connections** | Implemented | List, create, detail with sync state + call log |
| **Settings: Team** | Implemented | Member list, role change, remove |
| **Settings: Invitations** | Implemented | Send, list, resend, cancel |
| **Settings: General** | Implemented | Workspace name edit |
| **WebSocket** | Implemented | STOMP connect, subscribe, reconnect with backoff |
| **Toast notifications** | Implemented | Success/error/warning/info with auto-dismiss |
| **Command palette** | Scaffold | Component exists, search API connected, UI partial |
| **Tab system** | Scaffold | Store with persistence, tab bar component, partial integration |
| **Keyboard shortcuts** | Scaffold | Service with registration, Ctrl+K wired |
| **Notification bell** | Scaffold | Component + store, WebSocket integration done |
| **Status bar (sync)** | Scaffold | Store + component, WebSocket updates wired |
| **Operational grid** | Scaffold | Page component, AG Grid not yet integrated |
| **Analytics / P&L** | Scaffold | Layout component only |
| **Pricing** | Scaffold | Layout component only |
| **Promo** | Scaffold | Layout component only |
| **Detail panel content** | Scaffold | Service + shell slot, no entity-specific content |
| **i18n migration** | Partial | Some strings still hardcoded, `ru.json` exists |
| **Dark theme** | Not started | Token structure ready for swap |
| **Export (CSV/Excel)** | Not started | — |
| **Inline grid editing** | Not started | — |
| **Filter bar** | Not started | — |
| **Saved views** | Not started | — |

---

## 13. Key Dependencies

| Package | Version | Purpose |
|---|---|---|
| `@angular/core` | ^19.0.0 | Framework |
| `@angular/router` | ^19.0.0 | Client-side routing |
| `@angular/forms` | ^19.0.0 | Template-driven and reactive forms |
| `@angular/cdk` | ^19.0.0 | Unstyled UI primitives (overlay, a11y, drag-drop) |
| `@angular/animations` | ^19.0.0 | Animation support |
| `@ngrx/signals` | ^19.2.1 | Signal-based state management (SignalStore) |
| `@tanstack/angular-query-experimental` | ^5.96.0 | Server state management (cache-first fetching) |
| `@tanstack/query-core` | ^5.96.0 | TanStack Query core |
| `ag-grid-angular` | ^32.0.0 | Data grid (virtual scroll, column control, sorting) |
| `ag-grid-community` | ^32.0.0 | AG Grid core |
| `@ngx-translate/core` | ^16.0.0 | i18n string externalization |
| `@ngx-translate/http-loader` | ^16.0.0 | HTTP-based translation loading |
| `@stomp/rx-stomp` | ^2.0.0 | RxJS-native STOMP WebSocket client |
| `lucide-angular` | ^1.0.0 | Icon library (clean line icons) |
| `date-fns` | ^4.0.0 | Date formatting and manipulation |
| `rxjs` | ~7.8.0 | Reactive programming |
| `tailwindcss` | ^4.0.0 | Utility-first CSS framework |
| `typescript` | ~5.6.0 | Language |
| `zone.js` | ~0.15.0 | Angular change detection |

### Not using (and why)

| Library | Reason |
|---|---|
| Angular Material | Opinionated Material Design conflicts with custom Cursor-like look |
| NG-ZORRO / PrimeNG | Too opinionated, design system overhead |
| NgRx Store (full Redux) | Too heavy for current needs; SignalStore is sufficient |
| CSS-in-JS | Unnecessary with Tailwind |
| SSR / Angular Universal | SPA operational tool, no SSR needed |
| `angular-oauth2-oidc` | Listed in design direction but not used; auth delegated to oauth2-proxy + session cookies |

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — visual language, UX principles, component patterns
- [Navigation and Shell](navigation-and-shell.md) — shell zones, navigation model
- [Component Library](component-library.md) — shared component specifications
- [Pages: Auth & Onboarding](pages-auth-onboarding.md)
- [Pages: Settings](pages-settings.md)
- [Pages: Operational Grid](pages-operational-grid.md)
- [Pages: Analytics & P&L](pages-analytics-pnl.md)
- [Pages: Pricing](pages-pricing.md)
- [Pages: Promotions](pages-promotions.md)
- [Pages: Execution](pages-execution.md)
- [Pages: Alerts & Notifications](pages-alerts-notifications.md)
- [Pages: Working Queues](pages-working-queues.md)
- [Pages: Mismatch Monitor](pages-mismatch-monitor.md)
