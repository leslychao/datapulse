import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AlertRuleApiService } from '@core/api/alert-rule-api.service';
import { AlertRule, AlertRuleType, AlertSeverity } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';

const SEVERITY_COLORS: Record<AlertSeverity, StatusColor> = {
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'error',
};

interface ConfigFieldDef {
  key: string;
  labelKey: string;
  min?: number;
  max?: number;
  step?: number;
}

const CONFIG_FIELDS: Record<AlertRuleType, ConfigFieldDef[]> = {
  STALE_DATA: [
    { key: 'finance_stale_hours', labelKey: 'settings.alert_rules.config.finance_stale_hours', min: 1 },
    { key: 'state_stale_hours', labelKey: 'settings.alert_rules.config.state_stale_hours', min: 1 },
  ],
  MISSING_SYNC: [
    { key: 'expected_interval_minutes', labelKey: 'settings.alert_rules.config.expected_interval_minutes', min: 1 },
    { key: 'tolerance_factor', labelKey: 'settings.alert_rules.config.tolerance_factor', min: 1, step: 0.1 },
  ],
  RESIDUAL_ANOMALY: [
    { key: 'sigma_threshold', labelKey: 'settings.alert_rules.config.sigma_threshold', min: 0.5, max: 10, step: 0.1 },
    { key: 'min_absolute_threshold', labelKey: 'settings.alert_rules.config.min_absolute_threshold', min: 0 },
  ],
  SPIKE_DETECTION: [
    { key: 'spike_ratio_threshold', labelKey: 'settings.alert_rules.config.spike_ratio_threshold', min: 1, max: 100, step: 0.1 },
    { key: 'min_baseline_days', labelKey: 'settings.alert_rules.config.min_baseline_days', min: 1, max: 90 },
  ],
  MISMATCH: [
    { key: 'max_orphan_count', labelKey: 'settings.alert_rules.config.max_orphan_count', min: 0 },
  ],
  ACTION_FAILED: [{ key: 'consecutive_failures', labelKey: 'settings.alert_rules.config.consecutive_failures', min: 1 }],
  STUCK_STATE: [{ key: 'stuck_hours', labelKey: 'settings.alert_rules.config.stuck_hours', min: 1 }],
  RECONCILIATION_FAILED: [{ key: 'retry_exhausted', labelKey: 'settings.alert_rules.config.retry_exhausted', min: 0, max: 1 }],
  POISON_PILL: [{ key: 'enabled', labelKey: 'settings.alert_rules.config.enabled_flag', min: 0, max: 1 }],
  PROMO_MISMATCH: [{ key: 'max_delta_pct', labelKey: 'settings.alert_rules.config.max_delta_pct', min: 0, max: 100, step: 0.1 }],
  ACTION_DEFERRED: [{ key: 'defer_hours', labelKey: 'settings.alert_rules.config.defer_hours', min: 1 }],
};

@Component({
  selector: 'dp-alert-rules-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    SpinnerComponent,
    StatusBadgeComponent,
    DateFormatPipe,
  ],
  template: `
    <div class="max-w-5xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.alert_rules.title' | translate }}</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.alert_rules.subtitle' | translate }}</p>
      </div>

      @if (rulesQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (rulesQuery.data(); as rules) {
        <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.col_name' | translate }}</th>
                <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.col_type' | translate }}</th>
                <th class="px-4 py-2 text-center font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.col_severity' | translate }}</th>
                <th class="px-4 py-2 text-center font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.col_status' | translate }}</th>
                <th class="px-4 py-2 text-right font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.col_last_triggered' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (rule of rules; track rule.id) {
                <tr
                  class="border-b border-[var(--border-subtle)] cursor-pointer transition-colors hover:bg-[var(--bg-secondary)]"
                  [class.bg-[var(--bg-secondary)]]="expandedRuleId() === rule.id"
                  (click)="toggleExpand(rule)"
                >
                  <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ ruleTypeLabel(rule.ruleType) }}</td>
                  <td class="px-4 py-2.5 font-mono text-[var(--text-secondary)]">{{ rule.ruleType }}</td>
                  <td class="px-4 py-2.5 text-center">
                    <dp-status-badge [label]="severityLabel(rule.severity)" [color]="severityColor(rule.severity)" />
                  </td>
                  <td class="px-4 py-2.5 text-center">
                    <button
                      (click)="toggleEnabled(rule, $event)"
                      class="cursor-pointer rounded-full px-3 py-0.5 text-xs font-medium transition-colors"
                      [class]="rule.enabled
                        ? 'bg-[var(--status-success)]/15 text-[var(--status-success)]'
                        : 'bg-[var(--bg-tertiary)] text-[var(--text-tertiary)]'"
                    >
                      {{ rule.enabled ? ('settings.alert_rules.enabled' | translate) : ('settings.alert_rules.disabled' | translate) }}
                    </button>
                  </td>
                  <td class="px-4 py-2.5 text-right text-[var(--text-secondary)]">
                    {{ rule.lastTriggeredAt | dpDateFormat:'short' }}
                  </td>
                </tr>

                @if (expandedRuleId() === rule.id) {
                  <tr>
                    <td colspan="5" class="border-b border-[var(--border-subtle)] bg-[var(--bg-secondary)] px-6 py-5">
                      <div class="max-w-lg space-y-4">
                        <div>
                          <label class="mb-1 block text-sm font-medium text-[var(--text-secondary)]">{{ 'settings.alert_rules.severity_label' | translate }}</label>
                          <select
                            [ngModel]="editForm().severity"
                            (ngModelChange)="updateEditField('severity', $event)"
                            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                          >
                            @for (sev of severities; track sev) {
                              <option [value]="sev">{{ severityLabel(sev) }}</option>
                            }
                          </select>
                        </div>

                        <div class="flex items-center gap-3">
                          <label class="relative inline-flex cursor-pointer items-center">
                            <input
                              type="checkbox"
                              [ngModel]="editForm().blocksAutomation"
                              (ngModelChange)="updateEditField('blocksAutomation', $event)"
                              class="peer sr-only"
                            />
                            <div class="peer h-5 w-9 rounded-full bg-[var(--bg-tertiary)] after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:bg-white after:transition-all peer-checked:bg-[var(--accent-primary)] peer-checked:after:translate-x-full"></div>
                          </label>
                          <span class="text-sm text-[var(--text-primary)]">{{ 'settings.alert_rules.blocks_automation' | translate }}</span>
                        </div>

                        @if (editForm().blocksAutomation) {
                          <p class="rounded-[var(--radius-sm)] bg-[var(--status-warning)]/10 px-3 py-2 text-xs text-[var(--status-warning)]">
                            ⚠ {{ 'settings.alert_rules.blocks_automation_warning' | translate }}
                          </p>
                        }

                        <div class="space-y-3">
                          <h4 class="text-sm font-medium text-[var(--text-primary)]">{{ 'settings.alert_rules.parameters' | translate }}</h4>
                          @for (field of configFields(rule.ruleType); track field.key) {
                            <div>
                              <label class="mb-1 block text-xs text-[var(--text-secondary)]">{{ field.labelKey | translate }}</label>
                              <input
                                type="number"
                                [ngModel]="editForm().config[field.key]"
                                (ngModelChange)="updateConfigField(field.key, $event)"
                                [min]="field.min ?? 0"
                                [max]="field.max ?? null"
                                [step]="field.step ?? 1"
                                class="w-40 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                              />
                            </div>
                          }
                        </div>

                        <div class="flex gap-3 pt-2">
                          <button
                            (click)="saveRule()"
                            [disabled]="saveMutation.isPending()"
                            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:opacity-50"
                          >
                            @if (saveMutation.isPending()) {
                              <span class="dp-spinner mr-1 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white"></span>
                            }
                            {{ 'actions.save' | translate }}
                          </button>
                          <button
                            (click)="expandedRuleId.set(null)"
                            class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                          >
                            {{ 'actions.cancel' | translate }}
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>

        <p class="mt-3 text-xs text-[var(--text-tertiary)]">{{ 'settings.alert_rules.click_to_edit' | translate }}</p>
      }
    </div>
  `,
})
export class AlertRulesPageComponent {
  private readonly alertRuleApi = inject(AlertRuleApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly expandedRuleId = signal<number | null>(null);
  readonly editForm = signal<{
    severity: AlertSeverity;
    blocksAutomation: boolean;
    config: Record<string, number>;
  }>({ severity: 'WARNING', blocksAutomation: false, config: {} });

  readonly severities: AlertSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];

  readonly rulesQuery = injectQuery(() => ({
    queryKey: ['alert-rules'],
    queryFn: () => lastValueFrom(this.alertRuleApi.listAlertRules()),
  }));

  readonly saveMutation = injectMutation(() => ({
    mutationFn: (vars: { id: number; req: { config: Record<string, number>; enabled: boolean; severity: AlertSeverity; blocksAutomation: boolean } }) =>
      lastValueFrom(this.alertRuleApi.updateAlertRule(vars.id, vars.req)),
    onSuccess: () => {
      this.rulesQuery.refetch();
      this.expandedRuleId.set(null);
      this.toast.success(this.translate.instant('settings.alert_rules.saved'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.alert_rules.error_save')),
  }));

  readonly toggleMutation = injectMutation(() => ({
    mutationFn: (vars: { id: number; enabled: boolean }) =>
      lastValueFrom(this.alertRuleApi.toggleEnabled(vars.id, vars.enabled)),
    onSuccess: () => {
      this.rulesQuery.refetch();
      this.toast.success(this.translate.instant('settings.alert_rules.toggled'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.alert_rules.error_toggle')),
  }));

  ruleTypeLabel(type: AlertRuleType): string {
    return this.translate.instant(`settings.alert_rules.type.${type}`);
  }

  severityLabel(severity: AlertSeverity): string {
    return this.translate.instant(`settings.alert_rules.severity.${severity}`);
  }

  severityColor(severity: AlertSeverity): StatusColor {
    return SEVERITY_COLORS[severity] ?? 'neutral';
  }

  configFields(ruleType: AlertRuleType): ConfigFieldDef[] {
    return CONFIG_FIELDS[ruleType] ?? [];
  }

  toggleExpand(rule: AlertRule): void {
    if (this.expandedRuleId() === rule.id) {
      this.expandedRuleId.set(null);
      return;
    }
    this.expandedRuleId.set(rule.id);
    this.editForm.set({
      severity: rule.severity,
      blocksAutomation: rule.blocksAutomation,
      config: { ...rule.config },
    });
  }

  toggleEnabled(rule: AlertRule, event: MouseEvent): void {
    event.stopPropagation();
    this.toggleMutation.mutate({ id: rule.id, enabled: !rule.enabled });
  }

  updateEditField(field: 'severity' | 'blocksAutomation', value: AlertSeverity | boolean): void {
    this.editForm.update((f) => ({ ...f, [field]: value }));
  }

  updateConfigField(key: string, value: number): void {
    this.editForm.update((f) => ({
      ...f,
      config: { ...f.config, [key]: value },
    }));
  }

  saveRule(): void {
    const ruleId = this.expandedRuleId();
    if (!ruleId) return;

    const rules = this.rulesQuery.data();
    const currentRule = rules?.find((r) => r.id === ruleId);
    if (!currentRule) return;

    const form = this.editForm();
    this.saveMutation.mutate({
      id: ruleId,
      req: {
        config: form.config,
        enabled: currentRule.enabled,
        severity: form.severity,
        blocksAutomation: form.blocksAutomation,
      },
    });
  }
}
