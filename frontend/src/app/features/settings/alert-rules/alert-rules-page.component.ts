import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AlertRuleApiService } from '@core/api/alert-rule-api.service';
import { AlertRule, AlertRuleType, AlertSeverity } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';

const RULE_TYPE_LABELS: Record<AlertRuleType, string> = {
  STALE_DATA: 'Устаревшие данные',
  MISSING_SYNC: 'Пропуск синхронизации',
  RESIDUAL_ANOMALY: 'Аномалия reconciliation',
  SPIKE_DETECTION: 'Всплеск метрик',
  MISMATCH: 'Расхождения данных',
};

const SEVERITY_LABELS: Record<AlertSeverity, string> = {
  INFO: 'Инфо',
  WARNING: 'Внимание',
  CRITICAL: 'Критический',
};

const SEVERITY_COLORS: Record<AlertSeverity, StatusColor> = {
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'error',
};

interface ConfigFieldDef {
  key: string;
  label: string;
  min?: number;
  max?: number;
  step?: number;
}

const CONFIG_FIELDS: Record<AlertRuleType, ConfigFieldDef[]> = {
  STALE_DATA: [
    { key: 'finance_stale_hours', label: 'Порог устаревания финансов (часы)', min: 1 },
    { key: 'state_stale_hours', label: 'Порог устаревания каталога/цен/остатков (часы)', min: 1 },
  ],
  MISSING_SYNC: [
    { key: 'expected_interval_minutes', label: 'Ожидаемый интервал синхронизации (мин)', min: 1 },
    { key: 'tolerance_factor', label: 'Множитель допуска', min: 1, step: 0.1 },
  ],
  RESIDUAL_ANOMALY: [
    { key: 'sigma_threshold', label: 'Порог отклонения (σ)', min: 0.5, max: 10, step: 0.1 },
    { key: 'min_absolute_threshold', label: 'Минимальная сумма аномалии (₽)', min: 0 },
  ],
  SPIKE_DETECTION: [
    { key: 'spike_ratio_threshold', label: 'Порог всплеска (множитель)', min: 1, max: 100, step: 0.1 },
    { key: 'min_baseline_days', label: 'Минимум дней для baseline', min: 1, max: 90 },
  ],
  MISMATCH: [
    { key: 'max_orphan_count', label: 'Максимум расхождений без алерта', min: 0 },
  ],
};

@Component({
  selector: 'dp-alert-rules-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    SpinnerComponent,
    StatusBadgeComponent,
    DateFormatPipe,
  ],
  template: `
    <div class="max-w-5xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">Правила алертов</h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">Настройка бизнес-алертов и порогов срабатывания</p>
      </div>

      @if (rulesQuery.isPending()) {
        <dp-spinner message="Загрузка..." />
      }

      @if (rulesQuery.data(); as rules) {
        <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Название</th>
                <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">Тип</th>
                <th class="px-4 py-2 text-center font-medium text-[var(--text-secondary)]">Критичность</th>
                <th class="px-4 py-2 text-center font-medium text-[var(--text-secondary)]">Статус</th>
                <th class="px-4 py-2 text-right font-medium text-[var(--text-secondary)]">Последнее</th>
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
                      {{ rule.enabled ? 'Вкл' : 'Выкл' }}
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
                          <label class="mb-1 block text-sm font-medium text-[var(--text-secondary)]">Критичность</label>
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
                          <span class="text-sm text-[var(--text-primary)]">Блокирует автоматизацию</span>
                        </div>

                        @if (editForm().blocksAutomation) {
                          <p class="rounded-[var(--radius-sm)] bg-[var(--status-warning)]/10 px-3 py-2 text-xs text-[var(--status-warning)]">
                            ⚠ При срабатывании этого правила автоматическое ценообразование и промо будут приостановлены.
                          </p>
                        }

                        <div class="space-y-3">
                          <h4 class="text-sm font-medium text-[var(--text-primary)]">Параметры</h4>
                          @for (field of configFields(rule.ruleType); track field.key) {
                            <div>
                              <label class="mb-1 block text-xs text-[var(--text-secondary)]">{{ field.label }}</label>
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
                            Сохранить
                          </button>
                          <button
                            (click)="expandedRuleId.set(null)"
                            class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                          >
                            Отмена
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

        <p class="mt-3 text-xs text-[var(--text-tertiary)]">Нажмите на правило для настройки</p>
      }
    </div>
  `,
})
export class AlertRulesPageComponent {
  private readonly alertRuleApi = inject(AlertRuleApiService);
  private readonly toast = inject(ToastService);

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
      this.toast.success('Правило обновлено');
    },
    onError: () => this.toast.error('Не удалось сохранить'),
  }));

  readonly toggleMutation = injectMutation(() => ({
    mutationFn: (vars: { id: number; enabled: boolean }) =>
      lastValueFrom(this.alertRuleApi.toggleEnabled(vars.id, vars.enabled)),
    onSuccess: () => {
      this.rulesQuery.refetch();
      this.toast.success('Статус правила обновлён');
    },
    onError: () => this.toast.error('Не удалось изменить статус правила'),
  }));

  ruleTypeLabel(type: AlertRuleType): string {
    return RULE_TYPE_LABELS[type] ?? type;
  }

  severityLabel(severity: AlertSeverity): string {
    return SEVERITY_LABELS[severity] ?? severity;
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
