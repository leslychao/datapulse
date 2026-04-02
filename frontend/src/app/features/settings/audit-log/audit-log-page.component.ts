import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, ChevronRight, ChevronDown } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AuditLogApiService } from '@core/api/audit-log-api.service';
import { AuditLogEntry, AuditOutcome } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';

const OUTCOME_COLORS: Record<AuditOutcome, StatusColor> = {
  SUCCESS: 'success',
  DENIED: 'error',
  FAILED: 'warning',
};

@Component({
  selector: 'dp-audit-log-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    JsonPipe,
    LucideAngularModule,
    TranslatePipe,
    SpinnerComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    FilterBarComponent,
    DateFormatPipe,
  ],
  template: `
    <div class="max-w-6xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.audit_log.title' | translate }}</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.audit_log.subtitle' | translate }}</p>
      </div>

      <dp-filter-bar
        [filters]="filterConfigs"
        [values]="filterValues()"
        (filtersChanged)="onFiltersChanged($event)"
      />

      @if (auditQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (auditQuery.data(); as page) {
        @if (page.content.length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters() ? ('settings.audit_log.empty_filtered' | translate) : ('settings.audit_log.empty' | translate)"
            [hint]="hasActiveFilters() ? ('settings.audit_log.empty_filtered_hint' | translate) : ('settings.audit_log.empty_hint' | translate)"
            [actionLabel]="hasActiveFilters() ? ('settings.audit_log.reset_filters' | translate) : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <div class="mt-4 overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="w-8 px-2 py-2"></th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.audit_log.col_time' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.audit_log.col_user' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.audit_log.col_action' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.audit_log.col_entity' | translate }}</th>
                  <th class="px-4 py-2 text-center font-medium text-[var(--text-secondary)]">{{ 'settings.audit_log.col_outcome' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (entry of page.content; track entry.id) {
                  <tr
                    class="border-b border-[var(--border-subtle)] cursor-pointer transition-colors hover:bg-[var(--bg-secondary)]"
                    [class.bg-[var(--bg-secondary)]]="expandedId() === entry.id"
                    (click)="toggleExpand(entry.id)"
                  >
                    <td class="px-2 py-2.5 text-center text-[var(--text-tertiary)]">
                      <lucide-icon
                        [img]="expandedId() === entry.id ? ChevronDownIcon : ChevronRightIcon"
                        [size]="14"
                      />
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">
                      {{ entry.createdAt | dpDateFormat:'full' }}
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">
                      {{ actorDisplayName(entry) }}
                    </td>
                    <td class="px-4 py-2.5 font-mono text-[var(--text-secondary)]">
                      {{ entry.actionType }}
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                      {{ entry.entityType }}{{ entry.entityId ? '#' + entry.entityId : '' }}
                    </td>
                    <td class="px-4 py-2.5 text-center">
                      <dp-status-badge
                        [label]="outcomeLabel(entry.outcome)"
                        [color]="outcomeColor(entry.outcome)"
                      />
                    </td>
                  </tr>

                  @if (expandedId() === entry.id) {
                    <tr>
                      <td colspan="6" class="border-b border-[var(--border-subtle)] bg-[var(--bg-secondary)] px-8 py-4">
                        <div class="grid grid-cols-2 gap-x-8 gap-y-2 text-sm">
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.id' | translate }}:</span>
                            <span class="ml-2 text-[var(--text-primary)]">{{ entry.id }}</span>
                          </div>
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.actor_type' | translate }}:</span>
                            <span class="ml-2 text-[var(--text-primary)]">{{ entry.actorType }}</span>
                          </div>
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.user' | translate }}:</span>
                            <span class="ml-2 text-[var(--text-primary)]">
                              {{ actorDisplayName(entry) }}
                              @if (entry.actorEmail) {
                                <span class="text-[var(--text-secondary)]">({{ entry.actorEmail }})</span>
                              }
                            </span>
                          </div>
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.ip' | translate }}:</span>
                            <span class="ml-2 font-mono text-[var(--text-primary)]">{{ entry.ipAddress || '—' }}</span>
                          </div>
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.entity_type' | translate }}:</span>
                            <span class="ml-2 text-[var(--text-primary)]">{{ entry.entityType }}</span>
                          </div>
                          <div>
                            <span class="text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.entity_id' | translate }}:</span>
                            <span class="ml-2 text-[var(--text-primary)]">{{ entry.entityId || '—' }}</span>
                          </div>
                        </div>

                        @if (entry.details) {
                          <div class="mt-4">
                            <span class="text-sm text-[var(--text-tertiary)]">{{ 'settings.audit_log.detail.details' | translate }}:</span>
                            <pre
                              class="mt-1 max-h-48 overflow-auto rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-3 font-mono text-xs text-[var(--text-primary)]"
                            >{{ entry.details | json }}</pre>
                          </div>
                        }
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>

          @if (page.totalPages > 1) {
            <div class="mt-4 flex items-center justify-between text-sm text-[var(--text-secondary)]">
              <span>
                {{ 'pagination.showing' | translate:{ from: page.number * page.size + 1, to: Math.min((page.number + 1) * page.size, page.totalElements), total: page.totalElements } }}
              </span>
              <div class="flex gap-2">
                <button
                  (click)="goToPage(page.number - 1)"
                  [disabled]="page.number === 0"
                  class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {{ 'common.prev' | translate }}
                </button>
                <button
                  (click)="goToPage(page.number + 1)"
                  [disabled]="page.number >= page.totalPages - 1"
                  class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-sm transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {{ 'common.next' | translate }}
                </button>
              </div>
            </div>
          }
        }
      }
    </div>
  `,
})
export class AuditLogPageComponent {
  protected readonly ChevronRightIcon = ChevronRight;
  protected readonly ChevronDownIcon = ChevronDown;
  protected readonly Math = Math;

  private readonly auditLogApi = inject(AuditLogApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly expandedId = signal<number | null>(null);
  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'actionType',
      label: 'settings.audit_log.filter.action_type',
      type: 'select',
      options: [
        { value: 'connection', label: 'settings.audit_log.filter.action_group.connection' },
        { value: 'credential', label: 'settings.audit_log.filter.action_group.credential' },
        { value: 'member', label: 'settings.audit_log.filter.action_group.member' },
        { value: 'workspace', label: 'settings.audit_log.filter.action_group.workspace' },
        { value: 'policy', label: 'settings.audit_log.filter.action_group.policy' },
        { value: 'action', label: 'settings.audit_log.filter.action_group.action' },
        { value: 'promo', label: 'settings.audit_log.filter.action_group.promo' },
        { value: 'alert', label: 'settings.audit_log.filter.action_group.alert' },
      ],
    },
    {
      key: 'entityType',
      label: 'settings.audit_log.filter.entity_type',
      type: 'select',
      options: [
        { value: 'marketplace_connection', label: 'marketplace_connection' },
        { value: 'workspace_invitation', label: 'workspace_invitation' },
        { value: 'workspace_member', label: 'workspace_member' },
        { value: 'workspace', label: 'workspace' },
        { value: 'price_policy', label: 'price_policy' },
        { value: 'price_action', label: 'price_action' },
        { value: 'app_user', label: 'app_user' },
      ],
    },
    {
      key: 'period',
      label: 'settings.audit_log.filter.period',
      type: 'date-range',
    },
  ];

  protected readonly hasActiveFilters = computed(() => {
    const vals = this.filterValues();
    return Object.values(vals).some(
      (v) => v !== '' && v !== null && v !== undefined,
    );
  });

  readonly auditQuery = injectQuery(() => {
    const filters = this.filterValues();
    const page = this.currentPage();
    const period = filters['period'];

    return {
      queryKey: ['audit-log', filters, page],
      queryFn: () =>
        lastValueFrom(
          this.auditLogApi.listAuditLog({
            actionType: filters['actionType'] || undefined,
            entityType: filters['entityType'] || undefined,
            from: period?.from || undefined,
            to: period?.to || undefined,
            page,
            size: 50,
          }),
        ),
    };
  });

  actorDisplayName(entry: AuditLogEntry): string {
    if (entry.actorType === 'SYSTEM') return this.translate.instant('settings.audit_log.actor.SYSTEM');
    if (entry.actorType === 'SCHEDULER') return this.translate.instant('settings.audit_log.actor.SCHEDULER');
    return entry.actorName || this.translate.instant('settings.audit_log.actor.user_fallback');
  }

  outcomeLabel(outcome: AuditOutcome): string {
    return this.translate.instant(`settings.audit_log.outcome.${outcome}`);
  }

  outcomeColor(outcome: AuditOutcome): StatusColor {
    return OUTCOME_COLORS[outcome] ?? 'neutral';
  }

  toggleExpand(id: number): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
    this.expandedId.set(null);
  }
}
