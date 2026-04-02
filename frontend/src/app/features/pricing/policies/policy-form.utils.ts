import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

import {
  CommissionSource,
  GuardConfig,
  PolicyExecutionMode,
  PriceCorridorParams,
  RoundingDirection,
  StrategyType,
  TargetMarginParams,
} from '@core/models';

export function buildPolicyForm(fb: FormBuilder): FormGroup {
  return fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    strategyType: ['TARGET_MARGIN' as StrategyType, Validators.required],
    executionMode: ['RECOMMENDATION' as PolicyExecutionMode, Validators.required],
    priority: [0, [Validators.required, Validators.min(0)]],
    approvalTimeoutHours: [72, [Validators.required, Validators.min(1)]],

    targetMargin: fb.group({
      targetMarginPct: [
        null as number | null,
        [Validators.required, Validators.min(1), Validators.max(80)],
      ],
      commissionSource: ['AUTO_WITH_MANUAL_FALLBACK' as CommissionSource],
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

    corridor: fb.group(
      {
        corridorMinPrice: [null as number | null, [Validators.min(0.01)]],
        corridorMaxPrice: [null as number | null, [Validators.min(0.01)]],
      },
      { validators: [corridorValidator] },
    ),

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
  }, { validators: [maxPriceGtMinValidator] });
}

export function corridorValidator(group: AbstractControl): ValidationErrors | null {
  const min = group.get('corridorMinPrice')?.value;
  const max = group.get('corridorMaxPrice')?.value;
  if (min == null && max == null) return { corridorAtLeastOne: true };
  if (min != null && max != null && max <= min) return { corridorMaxGtMin: true };
  return null;
}

export function maxPriceGtMinValidator(group: AbstractControl): ValidationErrors | null {
  const min = group.get('minPrice')?.value;
  const max = group.get('maxPrice')?.value;
  if (min != null && max != null && max <= min) return { maxPriceGtMin: true };
  return null;
}

export function buildStrategyParams(
  raw: Record<string, unknown>,
): TargetMarginParams | PriceCorridorParams {
  if (raw['strategyType'] === 'TARGET_MARGIN') {
    const tm = raw['targetMargin'] as Record<string, unknown>;
    return {
      targetMarginPct: tm['targetMarginPct'] as number,
      commissionSource: tm['commissionSource'] as CommissionSource,
      commissionManualPct: tm['commissionManualPct'] as number | null,
      commissionLookbackDays: tm['commissionLookbackDays'] as number,
      commissionMinTransactions: tm['commissionMinTransactions'] as number,
      logisticsSource: tm['logisticsSource'] as CommissionSource,
      logisticsManualAmount: tm['logisticsManualAmount'] as number | null,
      includeReturnAdjustment: tm['includeReturnAdjustment'] as boolean,
      includeAdCost: tm['includeAdCost'] as boolean,
      roundingStep: tm['roundingStep'] as number,
      roundingDirection: tm['roundingDirection'] as RoundingDirection,
    } satisfies TargetMarginParams;
  }
  const corridor = raw['corridor'] as Record<string, unknown>;
  return {
    corridorMinPrice: corridor['corridorMinPrice'] as number | null,
    corridorMaxPrice: corridor['corridorMaxPrice'] as number | null,
  } satisfies PriceCorridorParams;
}

export function buildGuardConfig(raw: Record<string, unknown>): GuardConfig {
  return {
    marginGuardEnabled: raw['marginGuardEnabled'] as boolean,
    frequencyGuardEnabled: raw['frequencyGuardEnabled'] as boolean,
    frequencyGuardHours: raw['frequencyGuardHours'] as number,
    volatilityGuardEnabled: raw['volatilityGuardEnabled'] as boolean,
    volatilityGuardReversals: raw['volatilityGuardReversals'] as number,
    volatilityGuardPeriodDays: raw['volatilityGuardPeriodDays'] as number,
    promoGuardEnabled: raw['promoGuardEnabled'] as boolean,
    stockOutGuardEnabled: raw['stockOutGuardEnabled'] as boolean,
    staleDataGuardHours: raw['staleDataGuardHours'] as number,
  };
}

export function collectPolicyValidationErrors(
  form: FormGroup,
  translate: TranslateService,
): string[] {
  const errors: string[] = [];
  const t = (key: string) => translate.instant(key) as string;
  const strategyType = form.get('strategyType')!.value;

  const nameCtrl = form.get('name')!;
  if (nameCtrl.hasError('required')) errors.push(t('pricing.form.validation.name_required'));
  if (nameCtrl.hasError('maxlength')) errors.push(t('pricing.form.validation.name_maxlength'));

  if (strategyType === 'TARGET_MARGIN') {
    const g = form.get('targetMargin')!;
    if (g.get('targetMarginPct')!.hasError('required')) errors.push(t('pricing.form.validation.target_margin_required'));
    if (g.get('targetMarginPct')!.hasError('min') || g.get('targetMarginPct')!.hasError('max'))
      errors.push(t('pricing.form.validation.target_margin_range'));
    if (g.get('commissionManualPct')!.hasError('min') || g.get('commissionManualPct')!.hasError('max'))
      errors.push(t('pricing.form.validation.commission_manual_range'));
    if (g.get('commissionLookbackDays')!.hasError('min') || g.get('commissionLookbackDays')!.hasError('max'))
      errors.push(t('pricing.form.validation.commission_lookback_range'));
    if (g.get('commissionMinTransactions')!.hasError('min') || g.get('commissionMinTransactions')!.hasError('max'))
      errors.push(t('pricing.form.validation.commission_min_txn_range'));
    if (g.get('logisticsManualAmount')!.hasError('min'))
      errors.push(t('pricing.form.validation.logistics_manual_min'));
    if (g.get('roundingStep')!.hasError('min') || g.get('roundingStep')!.hasError('max'))
      errors.push(t('pricing.form.validation.rounding_step_range'));
  } else {
    const c = form.get('corridor')!;
    if (c.hasError('corridorAtLeastOne')) errors.push(t('pricing.form.validation.corridor_at_least_one'));
    if (c.hasError('corridorMaxGtMin')) errors.push(t('pricing.form.validation.corridor_max_gt_min'));
  }

  if (form.get('minMarginPct')!.hasError('min') || form.get('minMarginPct')!.hasError('max'))
    errors.push(t('pricing.form.validation.min_margin_range'));
  if (form.get('maxPriceChangePct')!.hasError('min') || form.get('maxPriceChangePct')!.hasError('max'))
    errors.push(t('pricing.form.validation.max_price_change_range'));
  if (form.get('minPrice')!.hasError('min')) errors.push(t('pricing.form.validation.min_price_positive'));
  if (form.get('maxPrice')!.hasError('min')) errors.push(t('pricing.form.validation.max_price_positive'));
  if (form.hasError('maxPriceGtMin')) errors.push(t('pricing.form.validation.max_price_gt_min'));
  if (form.get('priority')!.hasError('min')) errors.push(t('pricing.form.validation.priority_min'));
  if (form.get('approvalTimeoutHours')!.hasError('min')) errors.push(t('pricing.form.validation.approval_timeout_min'));
  if (form.get('frequencyGuardHours')!.hasError('min') || form.get('frequencyGuardHours')!.hasError('max'))
    errors.push(t('pricing.form.validation.frequency_hours_range'));
  if (form.get('volatilityGuardReversals')!.hasError('min') || form.get('volatilityGuardReversals')!.hasError('max'))
    errors.push(t('pricing.form.validation.volatility_reversals_range'));
  if (form.get('volatilityGuardPeriodDays')!.hasError('min') || form.get('volatilityGuardPeriodDays')!.hasError('max'))
    errors.push(t('pricing.form.validation.volatility_period_range'));
  if (form.get('staleDataGuardHours')!.hasError('min') || form.get('staleDataGuardHours')!.hasError('max'))
    errors.push(t('pricing.form.validation.stale_data_hours_range'));

  return errors;
}
