import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  ShieldCheck,
  ShieldAlert,
  ShieldX,
  Activity,
  AlertTriangle,
  Zap,
  Clock,
  ChevronDown,
  ChevronUp,
  ExternalLink,
} from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import {
  ConnectionDataQuality,
  MarketplaceType,
  SyncDomainStatus,
  DataTrustLevel,
  DomainSeverity,
  DomainImpact,
  DOMAIN_IMPACT,
  SEVERITY_ORDER,
  getMarketplaceShortLabel,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { KpiCardComponent, KpiAccent } from '@shared/components/kpi-card.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { formatDateTime } from '@shared/utils/format.utils';

const DOMAIN_LABEL_KEYS: Record<string, string> = {
  finance: 'analytics.data_quality.domain.finance',
  orders: 'analytics.data_quality.domain.orders',
  stock: 'analytics.data_quality.domain.stock',
  catalog: 'analytics.data_quality.domain.catalog',
  advertising: 'analytics.data_quality.domain.advertising',
  returns: 'analytics.data_quality.domain.returns',
};

const STATUS_COLOR: Record<SyncDomainStatus, StatusColor> = {
  FRESH: 'success',
  STALE: 'warning',
  OVERDUE: 'error',
};

const TRUST_CONFIG: Record<DataTrustLevel, {
  icon: typeof ShieldCheck;
  bgClass: string;
  borderClass: string;
  textClass: string;
  iconClass: string;
}> = {
  trusted: {
    icon: ShieldCheck,
    bgClass: 'bg-[var(--status-success-bg)]',
    borderClass: 'border-l-[var(--status-success)]',
    textClass: 'text-[var(--status-success)]',
    iconClass: 'text-[var(--status-success)]',
  },
  limited: {
    icon: ShieldAlert,
    bgClass: 'bg-[var(--status-warning-bg)]',
    borderClass: 'border-l-[var(--status-warning)]',
    textClass: 'text-[var(--status-warning)]',
    iconClass: 'text-[var(--status-warning)]',
  },
  unreliable: {
    icon: ShieldX,
    bgClass: 'bg-[var(--status-error-bg)]',
    borderClass: 'border-l-[var(--status-error)]',
    textClass: 'text-[var(--status-error)]',
    iconClass: 'text-[var(--status-error)]',
  },
};

interface DomainCard {
  domain: string;
  overallStatus: SyncDomainStatus;
  impact: DomainImpact;
  connections: DomainConnectionInfo[];
  latestSync: string | null;
  lagText: string;
}

interface DomainConnectionInfo {
  connectionId: number;
  connectionName: string;
  marketplaceType: MarketplaceType;
  status: SyncDomainStatus;
  lastSuccessAt: string | null;
  lagText: string;
}

@Component({
  selector: 'dp-data-quality-status-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    KpiCardComponent,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    EmptyStateComponent,
  ],
  template: `
    <div class="flex h-full flex-col gap-4 pb-4">
      @if (statusQuery.isPending()) {
        <div class="dp-shimmer h-24 w-full rounded-[var(--radius-md)]"></div>
        <div class="flex gap-3">
          @for (_ of [1, 2, 3, 4]; track $index) {
            <div class="dp-shimmer h-[72px] flex-1 rounded-[var(--radius-lg)]"></div>
          }
        </div>
        @for (_ of [1, 2, 3]; track $index) {
          <div class="dp-shimmer h-20 w-full rounded-[var(--radius-md)]"></div>
        }
      } @else if (statusQuery.isError()) {
        <dp-empty-state
          [message]="t.instant('analytics.data_quality.load_error')"
          [hint]="t.instant('analytics.data_quality.load_error_hint')"
          [actionLabel]="t.instant('analytics.data_quality.retry')"
          (action)="statusQuery.refetch()"
        />
      } @else if (connections().length === 0) {
        <dp-empty-state
          [message]="t.instant('analytics.data_quality.empty.no_connections')"
          [hint]="t.instant('analytics.data_quality.empty.no_connections_hint')"
          [actionLabel]="t.instant('analytics.data_quality.empty.no_connections_action')"
          (action)="goToSettings()"
        />
      } @else if (allDomains().length === 0) {
        <dp-empty-state
          [message]="t.instant('analytics.data_quality.empty.no_sync')"
          [hint]="t.instant('analytics.data_quality.empty.no_sync_hint')"
        />
      } @else {
        <!-- Trust Banner -->
        <div
          class="flex items-start gap-4 rounded-[var(--radius-md)] border-l-4 px-5 py-4"
          [class]="trustConfig().bgClass + ' ' + trustConfig().borderClass"
        >
          <lucide-icon
            [img]="trustConfig().icon"
            [size]="28"
            [class]="trustConfig().iconClass + ' mt-0.5 shrink-0'"
          />
          <div class="flex min-w-0 flex-col gap-1">
            <span class="text-base font-semibold text-[var(--text-primary)]">
              {{ 'analytics.data_quality.trust.' + trustLevel() | translate }}
            </span>
            <span class="text-sm text-[var(--text-secondary)]">
              {{ 'analytics.data_quality.trust.' + trustLevel() + '_desc' | translate }}
            </span>
            <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
              {{ 'analytics.data_quality.trust.recommendation.' + trustLevel() | translate }}
            </span>
            @if (latestUpdateTime()) {
              <span class="mt-0.5 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                {{ 'analytics.data_quality.trust.last_update' | translate:{ time: latestUpdateTime() } }}
              </span>
            }
          </div>
        </div>

        <!-- Marketplace filter -->
        @if (availableMarketplaces().length > 1) {
          <div class="flex items-center gap-2">
            @for (mp of ['ALL'].concat(availableMarketplaces()); track mp) {
              <button
                type="button"
                class="cursor-pointer rounded-[var(--radius-md)] px-3 py-1.5 text-[length:var(--text-sm)] font-medium transition-colors"
                [class]="selectedMarketplace() === mp
                  ? 'bg-[var(--accent-primary)] text-white'
                  : 'bg-[var(--bg-secondary)] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]'"
                (click)="selectedMarketplace.set(mp)"
              >
                {{ mp === 'ALL' ? ('analytics.data_quality.filter.all_marketplaces' | translate) : mpShortLabel(mp) }}
              </button>
            }
          </div>
        }

        <!-- KPI Strip -->
        <div class="flex flex-wrap gap-3">
          <dp-kpi-card
            [label]="t.instant('analytics.data_quality.kpi.domains_ok')"
            [value]="domainsOkLabel()"
            [icon]="icActivity"
            [accent]="domainsOkAccent()"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.data_quality.kpi.domains_problems')"
            [value]="problemDomainsCount()"
            [icon]="icAlertTriangle"
            [accent]="problemDomainsCount() > 0 ? 'error' : 'success'"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.data_quality.kpi.automation')"
            [value]="automationLabel()"
            [icon]="icZap"
            [accent]="automationAccent()"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.data_quality.kpi.max_lag')"
            [value]="maxLagText()"
            [icon]="icClock"
            [accent]="maxLagAccent()"
          />
        </div>

        <!-- Domain Cards -->
        <div class="flex flex-col gap-3">
          @for (card of filteredDomainCards(); track card.domain) {
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]">
              <!-- Domain header (clickable) -->
              <button
                type="button"
                class="flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[var(--bg-secondary)]"
                (click)="toggleDomain(card.domain)"
              >
                <div class="flex flex-1 flex-wrap items-center gap-x-4 gap-y-1">
                  <span class="text-sm font-semibold text-[var(--text-primary)]">
                    {{ domainLabel(card.domain) }}
                  </span>
                  <dp-status-badge
                    [label]="syncStatusLabel(card.overallStatus)"
                    [color]="syncStatusColor(card.overallStatus)"
                  />
                  <span
                    class="rounded px-1.5 py-0.5 text-[11px] font-medium"
                    [class]="severityClass(card.impact.severity)"
                  >
                    {{ 'analytics.data_quality.severity.' + card.impact.severity | translate }}
                  </span>
                </div>

                <div class="flex shrink-0 items-center gap-4">
                  <div class="hidden text-right sm:block">
                    <div class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'analytics.data_quality.last_sync' | translate }}
                    </div>
                    <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                      {{ card.lagText }}
                    </div>
                  </div>
                  <div class="hidden text-right md:block">
                    <div class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'analytics.data_quality.affected' | translate }}
                    </div>
                    <div class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                      {{ card.impact.affectedAreasKey | translate }}
                    </div>
                  </div>
                  <lucide-icon
                    [img]="expandedDomains().has(card.domain) ? icChevronUp : icChevronDown"
                    [size]="16"
                    class="text-[var(--text-tertiary)]"
                  />
                </div>
              </button>

              <!-- Expanded detail -->
              @if (expandedDomains().has(card.domain)) {
                <div class="border-t border-[var(--border-default)]">
                  <!-- Affected areas (visible on mobile when collapsed) -->
                  <div class="border-b border-[var(--border-subtle)] px-4 py-2.5 md:hidden">
                    <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'analytics.data_quality.affected' | translate }}:
                    </span>
                    <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                      {{ card.impact.affectedAreasKey | translate }}
                    </span>
                  </div>

                  <!-- Per-connection table -->
                  <div class="overflow-x-auto">
                    <table class="dp-table">
                      <thead>
                        <tr>
                          <th>{{ 'analytics.data_quality.connection' | translate }}</th>
                          <th>{{ 'analytics.data_quality.status' | translate }}</th>
                          <th>{{ 'analytics.data_quality.last_sync' | translate }}</th>
                          <th>{{ 'analytics.data_quality.lag' | translate }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (conn of card.connections; track conn.connectionId) {
                          <tr>
                            <td>
                              <div class="flex items-center gap-2">
                                <dp-marketplace-badge [type]="conn.marketplaceType" />
                                <span class="text-[var(--text-primary)]">{{ conn.connectionName }}</span>
                              </div>
                            </td>
                            <td>
                              <dp-status-badge
                                [label]="syncStatusLabel(conn.status)"
                                [color]="syncStatusColor(conn.status)"
                              />
                            </td>
                            <td class="whitespace-nowrap text-[var(--text-secondary)]">
                              {{ fmtDateTime(conn.lastSuccessAt) }}
                            </td>
                            <td class="whitespace-nowrap text-[var(--text-secondary)]">
                              {{ conn.lagText }}
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>

                  <!-- Problem explanation + report link -->
                  @if (card.overallStatus !== 'FRESH' && card.impact.reportLink) {
                    <div class="flex items-center justify-between border-t border-[var(--border-subtle)] bg-[var(--bg-secondary)] px-4 py-2.5">
                      <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                        {{ 'analytics.data_quality.trust.recommendation.' + trustLevel() | translate }}
                      </span>
                      <button
                        type="button"
                        class="inline-flex cursor-pointer items-center gap-1 text-[length:var(--text-sm)] text-[var(--accent-primary)] transition-colors hover:underline"
                        (click)="navigateToReport(card.impact.reportLink!); $event.stopPropagation()"
                      >
                        {{ 'analytics.data_quality.go_to_report' | translate }}
                        <lucide-icon [img]="icExternalLink" [size]="13" />
                      </button>
                    </div>
                  }
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class DataQualityStatusPageComponent {
  readonly t = inject(TranslateService);
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);

  protected readonly mpShortLabel = getMarketplaceShortLabel;
  readonly icActivity = Activity;
  readonly icAlertTriangle = AlertTriangle;
  readonly icZap = Zap;
  readonly icClock = Clock;
  readonly icChevronDown = ChevronDown;
  readonly icChevronUp = ChevronUp;
  readonly icExternalLink = ExternalLink;

  readonly selectedMarketplace = signal('ALL');
  readonly expandedDomains = signal<Set<string>>(new Set());

  readonly statusQuery = injectQuery(() => ({
    queryKey: ['analytics', 'data-quality-status', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getDataQualityStatus(
          this.wsStore.currentWorkspaceId()!,
          {},
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly connections = computed<ConnectionDataQuality[]>(() =>
    this.statusQuery.data()?.connections ?? [],
  );

  readonly filteredConnections = computed(() => {
    const mp = this.selectedMarketplace();
    if (mp === 'ALL') return this.connections();
    return this.connections().filter((c) => c.marketplaceType === mp);
  });

  readonly availableMarketplaces = computed(() => {
    const types = new Set(this.connections().map((c) => c.marketplaceType));
    return [...types].sort();
  });

  readonly allDomains = computed(() =>
    this.filteredConnections().flatMap((c) => c.domains),
  );

  readonly trustLevel = computed<DataTrustLevel>(() => {
    const domains = this.allDomains();
    if (domains.length === 0) return 'trusted';
    if (domains.some((d) => d.status === 'OVERDUE')) return 'unreliable';
    if (domains.some((d) => d.status === 'STALE')) return 'limited';
    return 'trusted';
  });

  readonly trustConfig = computed(() => TRUST_CONFIG[this.trustLevel()]);

  readonly domainCards = computed<DomainCard[]>(() => {
    const conns = this.filteredConnections();
    const domainMap = new Map<string, DomainConnectionInfo[]>();

    for (const conn of conns) {
      for (const d of conn.domains) {
        const list = domainMap.get(d.domain) ?? [];
        list.push({
          connectionId: conn.connectionId,
          connectionName: conn.connectionName,
          marketplaceType: conn.marketplaceType as MarketplaceType,
          status: d.status,
          lastSuccessAt: d.lastSuccessAt,
          lagText: this.computeLagText(d.lastSuccessAt),
        });
        domainMap.set(d.domain, list);
      }
    }

    const cards: DomainCard[] = [];
    for (const [domain, connections] of domainMap) {
      const worstStatus = this.worstStatus(connections.map((c) => c.status));
      const impact = DOMAIN_IMPACT[domain] ?? {
        severity: 'info' as DomainSeverity,
        affectedAreasKey: domain,
        reportLink: null,
      };
      const latestSync = this.latestSyncTime(connections);

      cards.push({
        domain,
        overallStatus: worstStatus,
        impact,
        connections,
        latestSync,
        lagText: this.computeLagText(latestSync),
      });
    }

    cards.sort((a, b) => {
      const statusOrder: Record<SyncDomainStatus, number> = { OVERDUE: 0, STALE: 1, FRESH: 2 };
      const s = (statusOrder[a.overallStatus] ?? 3) - (statusOrder[b.overallStatus] ?? 3);
      if (s !== 0) return s;
      return (SEVERITY_ORDER[a.impact.severity] ?? 9) - (SEVERITY_ORDER[b.impact.severity] ?? 9);
    });

    return cards;
  });

  readonly filteredDomainCards = computed(() => this.domainCards());

  constructor() {
    effect(() => {
      const cards = this.domainCards();
      if (cards.length > 0 && this.expandedDomains().size === 0) {
        const firstProblem = cards.find((c) => c.overallStatus !== 'FRESH');
        if (firstProblem) {
          this.expandedDomains.set(new Set([firstProblem.domain]));
        }
      }
    });
  }

  readonly domainsOkCount = computed(() =>
    this.domainCards().filter((d) => d.overallStatus === 'FRESH').length,
  );

  readonly domainsOkLabel = computed(() =>
    `${this.domainsOkCount()} / ${this.domainCards().length}`,
  );

  readonly domainsOkAccent = computed<KpiAccent>(() => {
    if (this.domainCards().length === 0) return 'neutral';
    if (this.domainsOkCount() === this.domainCards().length) return 'success';
    if (this.domainsOkCount() === 0) return 'error';
    return 'warning';
  });

  readonly problemDomainsCount = computed(() =>
    this.domainCards().filter((d) => d.overallStatus !== 'FRESH').length,
  );

  readonly automationBlocked = computed(() =>
    this.filteredConnections().some((c) => c.automationBlocked),
  );

  readonly automationLabel = computed(() =>
    this.automationBlocked()
      ? this.t.instant('analytics.data_quality.kpi.automation_blocked')
      : this.t.instant('analytics.data_quality.kpi.automation_active'),
  );

  readonly automationAccent = computed<KpiAccent>(() =>
    this.automationBlocked() ? 'error' : 'success',
  );

  readonly maxLagMs = computed(() => {
    const domains = this.allDomains();
    if (domains.length === 0) return 0;
    const now = Date.now();
    let maxLag = 0;
    for (const d of domains) {
      if (!d.lastSuccessAt) return Infinity;
      const lag = now - new Date(d.lastSuccessAt).getTime();
      if (lag > maxLag) maxLag = lag;
    }
    return maxLag;
  });

  readonly maxLagText = computed(() => {
    const ms = this.maxLagMs();
    if (ms === Infinity) return this.t.instant('analytics.data_quality.lag.never');
    return this.formatLag(ms);
  });

  readonly maxLagAccent = computed<KpiAccent>(() => {
    const ms = this.maxLagMs();
    if (ms === Infinity) return 'error';
    const hours = ms / 3_600_000;
    if (hours < 6) return 'success';
    if (hours < 24) return 'warning';
    return 'error';
  });

  readonly latestUpdateTime = computed(() => {
    const domains = this.allDomains();
    let latest: string | null = null;
    for (const d of domains) {
      if (d.lastSuccessAt && (!latest || d.lastSuccessAt > latest)) {
        latest = d.lastSuccessAt;
      }
    }
    return latest ? formatDateTime(latest) : null;
  });

  toggleDomain(domain: string): void {
    const current = new Set(this.expandedDomains());
    if (current.has(domain)) {
      current.delete(domain);
    } else {
      current.add(domain);
    }
    this.expandedDomains.set(current);
  }

  domainLabel(domain: string): string {
    const key = DOMAIN_LABEL_KEYS[domain];
    return key ? this.t.instant(key) : domain;
  }

  syncStatusLabel(status: SyncDomainStatus): string {
    return this.t.instant(`analytics.data_quality.sync_status.${status}`);
  }

  syncStatusColor(status: SyncDomainStatus): StatusColor {
    return STATUS_COLOR[status] ?? 'neutral';
  }

  severityClass(severity: DomainSeverity): string {
    switch (severity) {
      case 'critical':
        return 'bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)] text-[var(--status-error)]';
      case 'warning':
        return 'bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)] text-[var(--status-warning)]';
      default:
        return 'bg-[color-mix(in_srgb,var(--status-info)_12%,transparent)] text-[var(--status-info)]';
    }
  }

  fmtDateTime(iso: string | null): string {
    return formatDateTime(iso);
  }

  navigateToReport(link: string): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate(['/workspace', wsId, 'analytics', ...link.split('/')]);
    }
  }

  goToSettings(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate(['/workspace', wsId, 'settings', 'connections']);
    }
  }

  private computeLagText(iso: string | null): string {
    if (!iso) return this.t.instant('analytics.data_quality.lag.never');
    const ms = Date.now() - new Date(iso).getTime();
    return this.formatLag(ms);
  }

  private formatLag(ms: number): string {
    if (ms < 60_000) return this.t.instant('analytics.data_quality.lag.just_now');
    const minutes = Math.floor(ms / 60_000);
    if (minutes < 60) return this.t.instant('analytics.data_quality.lag.minutes', { count: minutes });
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return this.t.instant('analytics.data_quality.lag.hours', { count: hours });
    const days = Math.floor(hours / 24);
    return this.t.instant('analytics.data_quality.lag.days', { count: days });
  }

  private worstStatus(statuses: SyncDomainStatus[]): SyncDomainStatus {
    if (statuses.includes('OVERDUE')) return 'OVERDUE';
    if (statuses.includes('STALE')) return 'STALE';
    return 'FRESH';
  }

  private latestSyncTime(connections: DomainConnectionInfo[]): string | null {
    let latest: string | null = null;
    for (const c of connections) {
      if (c.lastSuccessAt && (!latest || c.lastSuccessAt > latest)) {
        latest = c.lastSuccessAt;
      }
    }
    return latest;
  }
}
