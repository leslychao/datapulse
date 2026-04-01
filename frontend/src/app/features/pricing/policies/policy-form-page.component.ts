import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import {
  CommissionSource,
  CreatePolicyRequest,
  GuardConfig,
  PolicyExecutionMode,
  PriceCorridorParams,
  PricingPolicy,
  RoundingDirection,
  StrategyType,
  TargetMarginParams,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-policy-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './policy-form-page.component.html',
})
export class PolicyFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);

  readonly policyId = input<string>();
  readonly isEditMode = computed(() => !!this.policyId());
  readonly wsId = computed(() => this.wsStore.currentWorkspaceId()!);

  readonly form = this.buildForm();

  readonly policyQuery = injectQuery(() => ({
    queryKey: ['policy', this.policyId()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.getPolicy(this.wsId(), Number(this.policyId())),
      ),
    enabled: this.isEditMode(),
  }));

  readonly saveMutation = injectMutation(() => ({
    mutationFn: (req: CreatePolicyRequest) => {
      if (this.isEditMode()) {
        return lastValueFrom(
          this.pricingApi.updatePolicy(
            this.wsId(),
            Number(this.policyId()),
            req,
          ),
        );
      }
      return lastValueFrom(this.pricingApi.createPolicy(this.wsId(), req));
    },
    onSuccess: (result: PricingPolicy) => {
      if (this.isEditMode()) {
        this.toast.success(`Политика обновлена (v${result.version})`);
      } else {
        this.toast.success('Политика создана');
      }
      this.navigateToList();
    },
    onError: () => this.toast.error('Не удалось сохранить политику'),
  }));

  readonly strategyTypes: { value: StrategyType; label: string }[] = [
    { value: 'TARGET_MARGIN', label: 'Целевая маржа' },
    { value: 'PRICE_CORRIDOR', label: 'Ценовой коридор' },
  ];

  readonly executionModes: {
    value: PolicyExecutionMode;
    label: string;
    description: string;
  }[] = [
    {
      value: 'RECOMMENDATION',
      label: 'Рекомендация',
      description: 'Только рекомендации, без автоматического применения',
    },
    {
      value: 'SEMI_AUTO',
      label: 'Полуавтомат',
      description: 'Требуется ручное подтверждение перед применением',
    },
    {
      value: 'FULL_AUTO',
      label: 'Автомат',
      description: 'Изменения применяются автоматически без подтверждения',
    },
    {
      value: 'SIMULATED',
      label: 'Симуляция',
      description: 'Только расчёт, без создания действий',
    },
  ];

  readonly commissionSources: { value: CommissionSource; label: string }[] = [
    { value: 'AUTO', label: 'Автоматически' },
    { value: 'MANUAL', label: 'Вручную' },
    { value: 'AUTO_WITH_MANUAL_FALLBACK', label: 'Авто с ручным fallback' },
  ];

  readonly roundingDirections: { value: RoundingDirection; label: string }[] = [
    { value: 'FLOOR', label: 'Вниз' },
    { value: 'NEAREST', label: 'К ближайшему' },
    { value: 'CEIL', label: 'Вверх' },
  ];

  constructor() {
    effect(() => {
      const policy = this.policyQuery.data();
      if (policy) {
        this.patchForm(policy);
      }
    });
  }

  get strategyType(): StrategyType {
    return this.form.get('strategyType')!.value;
  }

  get executionMode(): PolicyExecutionMode {
    return this.form.get('executionMode')!.value;
  }

  get commissionSource(): CommissionSource {
    return this.form.get('targetMargin.commissionSource')!.value;
  }

  get logisticsSource(): CommissionSource {
    return this.form.get('targetMargin.logisticsSource')!.value;
  }

  onStrategyTypeChange(): void {
    if (this.strategyType === 'TARGET_MARGIN') {
      this.form.get('targetMargin')!.enable();
      this.form.get('corridor')!.disable();
    } else {
      this.form.get('targetMargin')!.disable();
      this.form.get('corridor')!.enable();
    }
  }

  submit(): void {
    if (this.form.invalid) return;

    const raw = this.form.getRawValue();
    const strategyParams = this.buildStrategyParams(raw);

    const req: CreatePolicyRequest = {
      name: raw.name,
      strategyType: raw.strategyType,
      strategyParams,
      executionMode: raw.executionMode,
      priority: raw.priority,
      approvalTimeoutHours: raw.approvalTimeoutHours,
      minMarginPct: raw.minMarginPct || null,
      maxPriceChangePct: raw.maxPriceChangePct || null,
      minPrice: raw.minPrice || null,
      maxPrice: raw.maxPrice || null,
      guardConfig: this.buildGuardConfig(raw),
    };

    this.saveMutation.mutate(req);
  }

  cancel(): void {
    this.navigateToList();
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      strategyType: ['TARGET_MARGIN' as StrategyType, Validators.required],
      executionMode: [
        'RECOMMENDATION' as PolicyExecutionMode,
        Validators.required,
      ],
      priority: [0, [Validators.required, Validators.min(0)]],
      approvalTimeoutHours: [72, [Validators.required, Validators.min(1)]],

      targetMargin: this.fb.group({
        targetMarginPct: [
          null as number | null,
          [Validators.required, Validators.min(1), Validators.max(80)],
        ],
        commissionSource: ['AUTO' as CommissionSource],
        commissionManualPct: [null as number | null, [Validators.min(1), Validators.max(50)]],
        commissionLookbackDays: [30, [Validators.min(7), Validators.max(365)]],
        commissionMinTransactions: [5, [Validators.min(1), Validators.max(100)]],
        logisticsSource: ['AUTO' as CommissionSource],
        logisticsManualAmount: [null as number | null, [Validators.min(0.01)]],
        includeReturnAdjustment: [false],
        includeAdCost: [false],
        roundingStep: [10, [Validators.min(1), Validators.max(100)]],
        roundingDirection: ['FLOOR' as RoundingDirection],
      }),

      corridor: this.fb.group({
        corridorMinPrice: [null as number | null, [Validators.min(0.01)]],
        corridorMaxPrice: [null as number | null, [Validators.min(0.01)]],
      }),

      minMarginPct: [null as number | null, [Validators.min(0), Validators.max(80)]],
      maxPriceChangePct: [null as number | null, [Validators.min(1), Validators.max(50)]],
      minPrice: [null as number | null, [Validators.min(0.01)]],
      maxPrice: [null as number | null, [Validators.min(0.01)]],

      marginGuardEnabled: [true],
      frequencyGuardEnabled: [true],
      frequencyGuardHours: [24, [Validators.min(1), Validators.max(720)]],
      volatilityGuardEnabled: [true],
      volatilityGuardReversals: [3, [Validators.min(1), Validators.max(20)]],
      volatilityGuardPeriodDays: [7, [Validators.min(1), Validators.max(90)]],
      promoGuardEnabled: [true],
      stockOutGuardEnabled: [true],
      staleDataGuardHours: [24, [Validators.min(1), Validators.max(168)]],
    });
  }

  private patchForm(policy: PricingPolicy): void {
    this.form.patchValue({
      name: policy.name,
      strategyType: policy.strategyType,
      executionMode: policy.executionMode,
      priority: policy.priority,
      approvalTimeoutHours: policy.approvalTimeoutHours,
      minMarginPct: policy.minMarginPct,
      maxPriceChangePct: policy.maxPriceChangePct,
      minPrice: policy.minPrice,
      maxPrice: policy.maxPrice,
      marginGuardEnabled: policy.guardConfig.marginGuardEnabled,
      frequencyGuardEnabled: policy.guardConfig.frequencyGuardEnabled,
      frequencyGuardHours: policy.guardConfig.frequencyGuardHours,
      volatilityGuardEnabled: policy.guardConfig.volatilityGuardEnabled,
      volatilityGuardReversals: policy.guardConfig.volatilityGuardReversals,
      volatilityGuardPeriodDays: policy.guardConfig.volatilityGuardPeriodDays,
      promoGuardEnabled: policy.guardConfig.promoGuardEnabled,
      stockOutGuardEnabled: policy.guardConfig.stockOutGuardEnabled,
      staleDataGuardHours: policy.guardConfig.staleDataGuardHours,
    });

    if (policy.strategyType === 'TARGET_MARGIN') {
      const params = policy.strategyParams as TargetMarginParams;
      this.form.get('targetMargin')!.patchValue(params);
    } else {
      const params = policy.strategyParams as PriceCorridorParams;
      this.form.get('corridor')!.patchValue(params);
    }

    this.onStrategyTypeChange();
  }

  private buildStrategyParams(
    raw: ReturnType<typeof this.form.getRawValue>,
  ): TargetMarginParams | PriceCorridorParams {
    if (raw.strategyType === 'TARGET_MARGIN') {
      const tm = raw.targetMargin;
      return {
        targetMarginPct: tm.targetMarginPct!,
        commissionSource: tm.commissionSource,
        commissionManualPct: tm.commissionManualPct,
        commissionLookbackDays: tm.commissionLookbackDays,
        commissionMinTransactions: tm.commissionMinTransactions,
        logisticsSource: tm.logisticsSource,
        logisticsManualAmount: tm.logisticsManualAmount,
        includeReturnAdjustment: tm.includeReturnAdjustment,
        includeAdCost: tm.includeAdCost,
        roundingStep: tm.roundingStep,
        roundingDirection: tm.roundingDirection,
      } satisfies TargetMarginParams;
    }

    return {
      corridorMinPrice: raw.corridor.corridorMinPrice,
      corridorMaxPrice: raw.corridor.corridorMaxPrice,
    } satisfies PriceCorridorParams;
  }

  private buildGuardConfig(
    raw: ReturnType<typeof this.form.getRawValue>,
  ): GuardConfig {
    return {
      marginGuardEnabled: raw.marginGuardEnabled,
      frequencyGuardEnabled: raw.frequencyGuardEnabled,
      frequencyGuardHours: raw.frequencyGuardHours,
      volatilityGuardEnabled: raw.volatilityGuardEnabled,
      volatilityGuardReversals: raw.volatilityGuardReversals,
      volatilityGuardPeriodDays: raw.volatilityGuardPeriodDays,
      promoGuardEnabled: raw.promoGuardEnabled,
      stockOutGuardEnabled: raw.stockOutGuardEnabled,
      staleDataGuardHours: raw.staleDataGuardHours,
    };
  }

  private navigateToList(): void {
    this.router.navigate([
      '/workspace',
      String(this.wsId()),
      'pricing',
      'policies',
    ]);
  }
}
