import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import { CreateQueueRequest, QueueMatchRule, QueueType } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

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
  readonly openChange = output<boolean>();
  readonly saved = output<number>();

  readonly queueTypes: QueueType[] = ['ATTENTION', 'DECISION', 'PROCESSING'];
  readonly entityTypes = ['price_action', 'marketplace_offer'] as const;
  readonly operators = ['EQ', 'NE', 'CONTAINS'] as const;

  readonly previewCount = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    queueType: this.fb.nonNullable.control<QueueType>('ATTENTION', Validators.required),
    entityType: this.fb.nonNullable.control<'price_action' | 'marketplace_offer'>(
      'price_action',
      Validators.required,
    ),
    rules: this.fb.array<RuleForm>([]),
  });

  get rules(): FormArray<RuleForm> {
    return this.form.controls.rules;
  }

  readonly previewMutation = injectMutation(() => ({
    mutationFn: (body: CreateQueueRequest['autoCriteria']) => {
      const wsId = this.wsStore.currentWorkspaceId();
      if (!wsId) {
        throw new Error('no workspace');
      }
      return lastValueFrom(this.queueApi.previewCount(wsId, body));
    },
    onSuccess: (res) => this.previewCount.set(res.matchCount),
    onError: () =>
      this.toast.error(this.translate.instant('queues.builder.preview_error')),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateQueueRequest) => {
      const wsId = this.wsStore.currentWorkspaceId();
      if (!wsId) {
        throw new Error('no workspace');
      }
      return lastValueFrom(this.queueApi.createQueue(wsId, req));
    },
    onSuccess: (q) => {
      this.toast.success(this.translate.instant('queues.builder.created'));
      this.saved.emit(q.queueId);
      this.close();
    },
    onError: () =>
      this.toast.error(this.translate.instant('queues.builder.create_error')),
  }));

  constructor() {
    effect(() => {
      if (this.open()) {
        this.form.reset({
          name: '',
          queueType: 'ATTENTION',
          entityType: 'price_action',
          rules: [],
        });
        while (this.rules.length) {
          this.rules.removeAt(0);
        }
        this.addRule();
        this.previewCount.set(null);
      }
    });
  }

  fieldOptions(): string[] {
    const e = this.form.controls.entityType.value;
    return e === 'marketplace_offer'
      ? ['offerName', 'skuCode', 'offerStatus', 'marketplaceType']
      : ['offerName', 'skuCode', 'actionStatus', 'lastError'];
  }

  addRule(): void {
    this.rules.push(
      this.fb.group({
        field: this.fb.nonNullable.control(this.fieldOptions()[0]),
        op: this.fb.nonNullable.control<'EQ' | 'NE' | 'CONTAINS'>('EQ'),
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
    if (!criteria) {
      return;
    }
    this.previewMutation.mutate(criteria);
  }

  private buildCriteria(): CreateQueueRequest['autoCriteria'] | null {
    const entityType = this.form.controls.entityType.value;
    const rawRules = this.rules.getRawValue() as {
      field: string;
      op: string;
      value: string;
    }[];
    const match_rules: QueueMatchRule[] = rawRules.map((r) => ({
      field: r.field,
      op: r.op,
      value: r.value,
    }));
    return {
      entity_type: entityType,
      match_rules,
    };
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const criteria = this.buildCriteria();
    const req: CreateQueueRequest = {
      name: this.form.controls.name.value.trim(),
      queueType: this.form.controls.queueType.value,
      autoCriteria: criteria,
    };
    this.createMutation.mutate(req);
  }
}
