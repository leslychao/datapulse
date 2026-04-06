import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

import {
  AnyStrategyParams,
  CommissionSource,
  CompetitorAnchorParams,
  CompetitorPriceAggregation,
  CompositeParams,
  GuardConfig,
  PolicyExecutionMode,
  PriceCorridorParams,
  RoundingDirection,
  StockBalancingParams,
  StrategyType,
  TargetMarginParams,
  VelocityAdaptiveParams,
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

    velocity: fb.group({
      decelerationThreshold: [70, [Validators.required, Validators.min(1), Validators.max(99)]],
      accelerationThreshold: [130, [Validators.required, Validators.min(101), Validators.max(500)]],
      decelerationDiscountPct: [5, [Validators.required, Validators.min(1), Validators.max(30)]],
      accelerationMarkupPct: [3, [Validators.required, Validators.min(1), Validators.max(20)]],
      minBaselineSales: [10, [Validators.required, Validators.min(1), Validators.max(1000)]],
      velocityWindowShortDays: [7, [Validators.required, Validators.min(3), Validators.max(14)]],
      velocityWindowLongDays: [30, [Validators.required, Validators.min(14), Validators.max(90)]],
      roundingStep: [10, [Validators.min(1), Validators.max(100)]],
      roundingDirection: ['FLOOR' as RoundingDirection],
    }),

    stock: fb.group(
      {
        criticalDaysOfCover: [7, [Validators.required, Validators.min(1), Validators.max(30)]],
        overstockDaysOfCover: [60, [Validators.required, Validators.min(30), Validators.max(365)]],
        stockoutMarkupPct: [5, [Validators.required, Validators.min(1), Validators.max(30)]],
        overstockDiscountFactor: [10, [Validators.required, Validators.min(1), Validators.max(50)]],
        maxDiscountPct: [20, [Validators.required, Validators.min(1), Validators.max(50)]],
        leadTimeDays: [14, [Validators.required, Validators.min(1), Validators.max(180)]],
        roundingStep: [10, [Validators.min(1), Validators.max(100)]],
        roundingDirection: ['FLOOR' as RoundingDirection],
      },
      { validators: [stockCriticalLtOverstockValidator] },
    ),

    competitor: fb.group({
      positionFactor: [1.0, [Validators.required, Validators.min(0.5), Validators.max(2.0)]],
      minMarginPct: [10, [Validators.required, Validators.min(1), Validators.max(50)]],
      aggregation: ['MIN' as CompetitorPriceAggregation],
      useMarginFloor: [true],
      roundingStep: [10, [Validators.min(1), Validators.max(100)]],
      roundingDirection: ['FLOOR' as RoundingDirection],
    }),

    compositeComponents: fb.array([] as FormGroup[]),
    compositeRounding: fb.group({
      roundingStep: [10, [Validators.min(1), Validators.max(100)]],
      roundingDirection: ['FLOOR' as RoundingDirection],
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

    confirmFullAuto: [false],
  }, { validators: [maxPriceGtMinValidator, fullAutoConfirmValidator] });
}

export function stockCriticalLtOverstockValidator(group: AbstractControl): ValidationErrors | null {
  const critical = group.get('criticalDaysOfCover')?.value;
  const overstock = group.get('overstockDaysOfCover')?.value;
  if (critical != null && overstock != null && critical >= overstock) {
    return { criticalLtOverstock: true };
  }
  return null;
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

export function fullAutoConfirmValidator(group: AbstractControl): ValidationErrors | null {
  const mode = group.get('executionMode')?.value;
  const confirmed = group.get('confirmFullAuto')?.value;
  if (mode === 'FULL_AUTO' && !confirmed) {
    group.get('confirmFullAuto')?.setErrors({ requiredTrue: true });
    return null;
  }
  const ctrl = group.get('confirmFullAuto');
  if (ctrl?.hasError('requiredTrue')) {
    ctrl.setErrors(null);
  }
  return null;
}

export function buildStrategyParams(
  raw: Record<string, unknown>,
): AnyStrategyParams {
  const strategyType = raw['strategyType'] as StrategyType;

  if (strategyType === 'TARGET_MARGIN') {
    const tm = raw['targetMargin'] as Record<string, unknown>;
    const rawMarginPct = tm['targetMarginPct'] as number | null;
    const rawCommissionPct = tm['commissionManualPct'] as number | null;
    return {
      targetMarginPct: rawMarginPct != null ? rawMarginPct / 100 : 0,
      commissionSource: tm['commissionSource'] as CommissionSource,
      commissionManualPct: rawCommissionPct != null ? rawCommissionPct / 100 : null,
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

  if (strategyType === 'VELOCITY_ADAPTIVE') {
    const v = raw['velocity'] as Record<string, unknown>;
    return {
      decelerationThreshold: v['decelerationThreshold'] != null ? (v['decelerationThreshold'] as number) / 100 : null,
      accelerationThreshold: v['accelerationThreshold'] != null ? (v['accelerationThreshold'] as number) / 100 : null,
      decelerationDiscountPct: v['decelerationDiscountPct'] != null ? (v['decelerationDiscountPct'] as number) / 100 : null,
      accelerationMarkupPct: v['accelerationMarkupPct'] != null ? (v['accelerationMarkupPct'] as number) / 100 : null,
      minBaselineSales: v['minBaselineSales'] as number | null,
      velocityWindowShortDays: v['velocityWindowShortDays'] as number | null,
      velocityWindowLongDays: v['velocityWindowLongDays'] as number | null,
      roundingStep: v['roundingStep'] as number,
      roundingDirection: v['roundingDirection'] as RoundingDirection,
    } satisfies VelocityAdaptiveParams;
  }

  if (strategyType === 'STOCK_BALANCING') {
    const s = raw['stock'] as Record<string, unknown>;
    return {
      criticalDaysOfCover: s['criticalDaysOfCover'] as number | null,
      overstockDaysOfCover: s['overstockDaysOfCover'] as number | null,
      stockoutMarkupPct: s['stockoutMarkupPct'] != null ? (s['stockoutMarkupPct'] as number) / 100 : null,
      overstockDiscountFactor: s['overstockDiscountFactor'] != null ? (s['overstockDiscountFactor'] as number) / 100 : null,
      maxDiscountPct: s['maxDiscountPct'] != null ? (s['maxDiscountPct'] as number) / 100 : null,
      leadTimeDays: s['leadTimeDays'] as number | null,
      roundingStep: s['roundingStep'] as number,
      roundingDirection: s['roundingDirection'] as RoundingDirection,
    } satisfies StockBalancingParams;
  }

  if (strategyType === 'COMPETITOR_ANCHOR') {
    const c = raw['competitor'] as Record<string, unknown>;
    return {
      positionFactor: c['positionFactor'] as number | null,
      minMarginPct: c['minMarginPct'] != null ? (c['minMarginPct'] as number) / 100 : null,
      aggregation: c['aggregation'] as CompetitorPriceAggregation,
      useMarginFloor: c['useMarginFloor'] as boolean,
      roundingStep: c['roundingStep'] as number,
      roundingDirection: c['roundingDirection'] as RoundingDirection,
    } satisfies CompetitorAnchorParams;
  }

  if (strategyType === 'COMPOSITE') {
    const comps = raw['compositeComponents'] as Record<string, unknown>[];
    const rounding = raw['compositeRounding'] as Record<string, unknown>;
    return {
      components: (comps ?? []).map((c) => ({
        strategyType: c['strategyType'] as StrategyType,
        weight: c['weight'] as number,
        strategyParams: (c['strategyParams'] as string) || '{}',
      })),
      roundingStep: (rounding?.['roundingStep'] as number) ?? 10,
      roundingDirection: (rounding?.['roundingDirection'] as RoundingDirection) ?? 'FLOOR',
    } satisfies CompositeParams;
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
    competitorFreshnessGuardEnabled: raw['competitorFreshnessGuardEnabled'] as boolean | undefined,
    competitorFreshnessHours: raw['competitorFreshnessHours'] as number | undefined,
    competitorTrustGuardEnabled: raw['competitorTrustGuardEnabled'] as boolean | undefined,
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
    if (g.get('commissionLookbackDays')!.invalid)
      errors.push(t('pricing.form.validation.commission_lookback_range'));
    if (g.get('commissionMinTransactions')!.invalid)
      errors.push(t('pricing.form.validation.commission_min_txn_range'));
    if (g.get('logisticsManualAmount')!.hasError('min'))
      errors.push(t('pricing.form.validation.logistics_manual_min'));
    if (g.get('roundingStep')!.invalid)
      errors.push(t('pricing.form.validation.rounding_step_range'));
  } else if (strategyType === 'VELOCITY_ADAPTIVE') {
    const g = form.get('velocity')!;
    if (g.get('decelerationThreshold')!.invalid) errors.push(t('pricing.form.validation.velocity.deceleration_threshold_range'));
    if (g.get('accelerationThreshold')!.invalid) errors.push(t('pricing.form.validation.velocity.acceleration_threshold_range'));
    if (g.get('decelerationDiscountPct')!.invalid) errors.push(t('pricing.form.validation.velocity.deceleration_discount_range'));
    if (g.get('accelerationMarkupPct')!.invalid) errors.push(t('pricing.form.validation.velocity.acceleration_markup_range'));
    if (g.get('minBaselineSales')!.invalid) errors.push(t('pricing.form.validation.velocity.min_baseline_range'));
    if (g.get('velocityWindowShortDays')!.invalid) errors.push(t('pricing.form.validation.velocity.short_window_range'));
    if (g.get('velocityWindowLongDays')!.invalid) errors.push(t('pricing.form.validation.velocity.long_window_range'));
  } else if (strategyType === 'STOCK_BALANCING') {
    const g = form.get('stock')!;
    if (g.get('criticalDaysOfCover')!.invalid) errors.push(t('pricing.form.validation.stock.critical_days_range'));
    if (g.get('overstockDaysOfCover')!.invalid) errors.push(t('pricing.form.validation.stock.overstock_days_range'));
    if (g.hasError('criticalLtOverstock')) errors.push(t('pricing.form.validation.stock.critical_lt_overstock'));
    if (g.get('stockoutMarkupPct')!.invalid) errors.push(t('pricing.form.validation.stock.stockout_markup_range'));
    if (g.get('overstockDiscountFactor')!.invalid) errors.push(t('pricing.form.validation.stock.overstock_factor_range'));
    if (g.get('maxDiscountPct')!.invalid) errors.push(t('pricing.form.validation.stock.max_discount_range'));
    if (g.get('leadTimeDays')!.invalid) errors.push(t('pricing.form.validation.stock.lead_time_range'));
  } else if (strategyType === 'COMPETITOR_ANCHOR') {
    const g = form.get('competitor')!;
    if (g.get('positionFactor')!.invalid) errors.push(t('pricing.form.validation.competitor.position_factor_range'));
    if (g.get('minMarginPct')!.invalid) errors.push(t('pricing.form.validation.competitor.min_margin_range'));
  } else if (strategyType === 'PRICE_CORRIDOR') {
    const c = form.get('corridor')!;
    if (c.hasError('corridorAtLeastOne')) errors.push(t('pricing.form.validation.corridor_at_least_one'));
    if (c.hasError('corridorMaxGtMin')) errors.push(t('pricing.form.validation.corridor_max_gt_min'));
  }

  if (form.get('minMarginPct')!.invalid)
    errors.push(t('pricing.form.validation.min_margin_range'));
  if (form.get('maxPriceChangePct')!.invalid)
    errors.push(t('pricing.form.validation.max_price_change_range'));
  if (form.get('minPrice')!.hasError('min')) errors.push(t('pricing.form.validation.min_price_positive'));
  if (form.get('maxPrice')!.hasError('min')) errors.push(t('pricing.form.validation.max_price_positive'));
  if (form.hasError('maxPriceGtMin')) errors.push(t('pricing.form.validation.max_price_gt_min'));
  if (form.get('priority')!.invalid) errors.push(t('pricing.form.validation.priority_min'));
  if (form.get('approvalTimeoutHours')!.invalid)
    errors.push(t('pricing.form.validation.approval_timeout_min'));
  if (form.get('frequencyGuardHours')!.invalid)
    errors.push(t('pricing.form.validation.frequency_hours_range'));
  if (form.get('volatilityGuardReversals')!.invalid)
    errors.push(t('pricing.form.validation.volatility_reversals_range'));
  if (form.get('volatilityGuardPeriodDays')!.invalid)
    errors.push(t('pricing.form.validation.volatility_period_range'));
  if (form.get('staleDataGuardHours')!.invalid)
    errors.push(t('pricing.form.validation.stale_data_hours_range'));
  if (form.get('confirmFullAuto')!.hasError('requiredTrue'))
    errors.push(t('pricing.form.validation.full_auto_confirm_required'));

  return errors;
}
