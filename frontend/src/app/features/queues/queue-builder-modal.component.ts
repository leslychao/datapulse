import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import {
  CreateQueueRequest,
  Queue,
  QueueAutoCriteria,
  QueueMatchRule,
  QueueType,
  UpdateQueueRequest,
  getMarketplaceShortLabel,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

interface CriteriaField {
  id: string;
  labelKey: string;
  type: 'enum' | 'number' | 'boolean' | 'text' | 'select';
  operators: string[];
  options?: string[];
}

const CRITERIA_FIELDS: CriteriaField[] = [
  { id: 'marketplace_type', labelKey: 'queues.builder.field.marketplace_type', type: 'enum', operators: ['eq', 'in'], options: ['WB', 'OZON', 'YANDEX'] },
  { id: 'connection_id', labelKey: 'queues.builder.field.connection_id', type: 'select', operators: ['eq', 'in'] },
  { id: 'category', labelKey: 'queues.builder.field.category', type: 'text', operators: ['eq', 'in'] },
  { id: 'brand', labelKey: 'queues.builder.field.brand', type: 'text', operators: ['eq', 'in'] },
  { id: 'stock_risk', labelKey: 'queues.builder.field.stock_risk', type: 'enum', operators: ['eq', 'in'], options: ['CRITICAL', 'WARNING', 'NORMAL'] },
  { id: 'has_active_policy', labelKey: 'queues.builder.field.has_active_policy', type: 'boolean', operators: ['eq'] },
  { id: 'margin_pct', labelKey: 'queues.builder.field.margin_pct', type: 'number', operators: ['gt', 'lt', 'gte', 'lte'] },
  { id: 'status', labelKey: 'queues.builder.field.status', type: 'enum', operators: ['eq', 'in'], options: ['ACTIVE', 'ARCHIVED', 'BLOCKED'] },
  { id: 'has_manual_lock', labelKey: 'queues.builder.field.has_manual_lock', type: 'boolean', operators: ['eq'] },
  { id: 'last_action_status', labelKey: 'queues.builder.field.last_action_status', type: 'enum', operators: ['eq', 'in'], options: ['FAILED', 'PENDING_APPROVAL', 'SUCCEEDED', 'ON_HOLD', 'CANCELLED'] },
  { id: 'promo_status', labelKey: 'queues.builder.field.promo_status', type: 'enum', operators: ['eq', 'in'], options: ['PARTICIPATING', 'ELIGIBLE'] },
];

const ALL_OPERATORS = ['eq', 'neq', 'in', 'gt', 'lt', 'gte', 'lte'] as const;

type RuleForm = FormGroup<{
  field: ReturnType<FormBuilder['control']>;
  op: ReturnType<FormBuilder['control']>;
  value: ReturnType<FormBuilder['control']>;
}>;

@Component({
  selector: 'dp-queue-builder-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './queue-builder-modal.component.html',
})
export class QueueBuilderModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly queueApi = inject(QueueApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly open = input(false);
  readonly editQueue = input<Queue | null>(null);
  readonly openChange = output<boolean>();
  readonly saved = output<number>();

  readonly queueTypes: QueueType[] = ['ATTENTION', 'DECISION', 'PROCESSING'];
  readonly criteriaFields = CRITERIA_FIELDS;
  protected readonly optionLabel = getMarketplaceShortLabel;

  readonly previewCount = signal<number | null>(null);
  readonly deleteConfirmOpen = signal(false);

  readonly isEditMode = computed(() => this.editQueue() != null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
    queueType: this.fb.nonNullable.control<QueueType>('ATTENTION', Validators.required),
    rules: this.fb.array<RuleForm>([]),
  });

  get rules(): FormArray<RuleForm> {
    return this.form.controls.rules;
  }

  readonly previewMutation = injectMutation(() => ({
    mutationFn: (body: QueueAutoCriteria) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      return lastValueFrom(this.queueApi.previewCount(wsId, body));
    },
    onSuccess: (res) => this.previewCount.set(res.matchCount),
    onError: () => this.toast.error(this.translate.instant('queues.builder.preview_error')),
  }));

  readonly saveMutation = injectMutation(() => ({
    mutationFn: (req: { isEdit: boolean; queueId?: number; body: CreateQueueRequest }) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      if (req.isEdit && req.queueId) {
        return lastValueFrom(this.queueApi.updateQueue(wsId, req.queueId, req.body));
      }
      return lastValueFrom(this.queueApi.createQueue(wsId, req.body));
    },
    onSuccess: (q) => {
      const key = this.isEditMode() ? 'queues.builder.updated' : 'queues.builder.created';
      this.toast.success(this.translate.instant(key));
      this.saved.emit(q.queueId);
      this.close();
    },
    onError: () => this.toast.error(this.translate.instant('queues.builder.create_error')),
  }));

  readonly deleteMutation = injectMutation(() => ({
    mutationFn: (queueId: number) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      return lastValueFrom(this.queueApi.deleteQueue(wsId, queueId));
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('queues.builder.deleted'));
      this.close();
    },
    onError: () => this.toast.error(this.translate.instant('queues.builder.delete_error')),
  }));

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const eq = this.editQueue();
      while (this.rules.length) this.rules.removeAt(0);
      this.previewCount.set(null);
      this.deleteConfirmOpen.set(false);

      if (eq) {
        this.form.patchValue({ name: eq.name, queueType: eq.queueType });
      } else {
        this.form.reset({ name: '', queueType: 'ATTENTION' });
        this.addRule();
      }
    });
  }

  fieldDef(fieldId: string): CriteriaField | undefined {
    return CRITERIA_FIELDS.find((f) => f.id === fieldId);
  }

  operatorsFor(fieldId: string): string[] {
    return this.fieldDef(fieldId)?.operators ?? ['eq', 'neq'];
  }

  optionsFor(fieldId: string): string[] | undefined {
    return this.fieldDef(fieldId)?.options;
  }

  addRule(): void {
    this.rules.push(
      this.fb.group({
        field: this.fb.nonNullable.control(CRITERIA_FIELDS[0].id),
        op: this.fb.nonNullable.control('eq'),
        value: this.fb.nonNullable.control(''),
      }) as RuleForm,
    );
  }

  removeRule(i: number): void {
    this.rules.removeAt(i);
  }

  close(): void {
    this.openChange.emit(false);
  }

  runPreview(): void {
    const criteria = this.buildCriteria();
    if (!criteria) return;
    this.previewMutation.mutate(criteria);
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const body: CreateQueueRequest = {
      name: this.form.controls.name.value.trim(),
      queueType: this.form.controls.queueType.value,
      autoCriteria: this.buildCriteria(),
    };

    const eq = this.editQueue();
    this.saveMutation.mutate({
      isEdit: eq != null,
      queueId: eq?.queueId,
      body,
    });
  }

  confirmDelete(): void {
    const eq = this.editQueue();
    if (eq) {
      this.deleteMutation.mutate(eq.queueId);
    }
  }

  private buildCriteria(): QueueAutoCriteria | null {
    const rawRules = this.rules.getRawValue() as { field: string; op: string; value: string }[];
    if (rawRules.length === 0) return null;
    const match_rules: QueueMatchRule[] = rawRules
      .filter((r) => r.value.trim() !== '')
      .map((r) => ({ field: r.field, op: r.op, value: r.value }));
    if (match_rules.length === 0) return null;
    return { entity_type: 'marketplace_offer', match_rules };
  }
}
