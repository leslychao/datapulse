import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';

import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Info, AlertTriangle, Eye } from 'lucide-angular';

import { PricingApiService } from '@core/api/pricing-api.service';
import { translateApiErrorMessage } from '@core/i18n/translate-api-error';
import {
  CreatePolicyRequest,
  PolicyExecutionMode,
  PriceCorridorParams,
  PricingPolicy,
  StrategyType,
  TargetMarginParams,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

import {
  buildPolicyForm,
  buildStrategyParams,
  buildGuardConfig,
  collectPolicyValidationErrors,
} from './policy-form.utils';
import { TargetMarginFormComponent } from './target-margin-form.component';
import { ConstraintsFormComponent } from './constraints-form.component';
import { GuardConfigFormComponent } from './guard-config-form.component';
import { ImpactPreviewModalComponent } from './impact-preview-modal.component';

@Component({
  selector: 'dp-policy-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    TargetMarginFormComponent,
    ConstraintsFormComponent,
    GuardConfigFormComponent,
    ImpactPreviewModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0 overflow-auto' },
  templateUrl: './policy-form-page.component.html',
})
export class PolicyFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly el = inject(ElementRef);
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queryClient = inject(QueryClient);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly icons = { Info, AlertTriangle, Eye };

  readonly policyId = input<string>();
  readonly isEditMode = computed(() => !!this.policyId());
  readonly wsId = computed(() => this.wsStore.currentWorkspaceId()!);

  readonly form = buildPolicyForm(this.fb);
  readonly submitted = signal(false);
  readonly showPreviewModal = signal(false);
  readonly formDirty = signal(false);
  private formPristineAfterPatch = false;

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
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      if (this.isEditMode()) {
        this.queryClient.invalidateQueries({ queryKey: ['policy', this.policyId()] });
        this.toast.success(
          this.translate.instant('pricing.policies.updated', {
            version: String(result.version),
          }),
        );
      } else {
        this.toast.success(this.translate.instant('pricing.policies.created'));
      }
      this.formDirty.set(false);
      this.navigateToList();
    },
    onError: (err) =>
      this.toast.error(
        translateApiErrorMessage(this.translate, err, 'pricing.policies.save_error'),
      ),
  }));

  readonly strategyTypes: { value: StrategyType; labelKey: string; descKey: string }[] = [
    {
      value: 'TARGET_MARGIN',
      labelKey: 'pricing.policies.strategy.TARGET_MARGIN',
      descKey: 'pricing.form.tooltip.strategy_type.TARGET_MARGIN',
    },
    {
      value: 'PRICE_CORRIDOR',
      labelKey: 'pricing.policies.strategy.PRICE_CORRIDOR',
      descKey: 'pricing.form.tooltip.strategy_type.PRICE_CORRIDOR',
    },
  ];

  readonly executionModes: {
    value: PolicyExecutionMode;
    labelKey: string;
    descKey: string;
  }[] = [
    {
      value: 'RECOMMENDATION',
      labelKey: 'pricing.policies.mode.RECOMMENDATION',
      descKey: 'pricing.form.tooltip.execution_mode.RECOMMENDATION',
    },
    {
      value: 'SEMI_AUTO',
      labelKey: 'pricing.policies.mode.SEMI_AUTO',
      descKey: 'pricing.form.tooltip.execution_mode.SEMI_AUTO',
    },
    {
      value: 'FULL_AUTO',
      labelKey: 'pricing.policies.mode.FULL_AUTO',
      descKey: 'pricing.form.tooltip.execution_mode.FULL_AUTO',
    },
    {
      value: 'SIMULATED',
      labelKey: 'pricing.policies.mode.SIMULATED',
      descKey: 'pricing.form.tooltip.execution_mode.SIMULATED',
    },
  ];

  constructor() {
    this.onStrategyTypeChange();

    effect(() => {
      const policy = this.policyQuery.data();
      if (policy) {
        this.patchForm(policy);
      }
    });

    this.form.valueChanges.subscribe(() => {
      if (this.formPristineAfterPatch) {
        this.formPristineAfterPatch = false;
        return;
      }
      this.formDirty.set(true);
    });
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.formDirty()) {
      event.preventDefault();
    }
  }

  canDeactivate(): boolean {
    if (!this.formDirty()) return true;
    return confirm(this.translate.instant('pricing.form.unsaved_changes'));
  }

  get strategyType(): StrategyType {
    return this.form.get('strategyType')!.value;
  }

  get strategyTypeDescription(): string {
    return this.strategyTypes.find(s => s.value === this.strategyType)?.descKey ?? '';
  }

  get executionMode(): PolicyExecutionMode {
    return this.form.get('executionMode')!.value;
  }

  get executionModeDescription(): string {
    return this.executionModes.find(m => m.value === this.executionMode)?.descKey ?? '';
  }

  readonly targetMarginGroup = this.form.get('targetMargin') as FormGroup;
  readonly corridorGroup = this.form.get('corridor') as FormGroup;

  onStrategyTypeChange(): void {
    if (this.strategyType === 'TARGET_MARGIN') {
      this.form.get('targetMargin')!.enable();
      this.form.get('corridor')!.disable();
    } else {
      this.form.get('targetMargin')!.disable();
      this.form.get('corridor')!.enable();
    }
  }

  onStrategyTypeUserChange(newType: string): void {
    const current = this.strategyType;
    if (current === newType) return;

    if (this.formDirty()) {
      const confirmed = confirm(
        this.translate.instant('pricing.form.strategy_switch_confirm'),
      );
      if (!confirmed) {
        this.form.get('strategyType')!.setValue(current, { emitEvent: false });
        return;
      }
    }
    this.form.get('strategyType')!.setValue(newType);
    this.onStrategyTypeChange();
  }

  submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      const validationErrors = this.collectValidationErrors();
      const message = validationErrors.length
        ? validationErrors[0]
        : this.translate.instant('pricing.form.has_errors');
      this.toast.error(message);
      this.scrollToFirstInvalidControl();
      return;
    }

    const raw = this.form.getRawValue();
    const req: CreatePolicyRequest = {
      name: raw.name,
      strategyType: raw.strategyType,
      strategyParams: buildStrategyParams(raw),
      executionMode: raw.executionMode,
      priority: raw.priority,
      approvalTimeoutHours: raw.approvalTimeoutHours,
      minMarginPct: raw.minMarginPct || null,
      maxPriceChangePct: raw.maxPriceChangePct || null,
      minPrice: raw.minPrice || null,
      maxPrice: raw.maxPrice || null,
      guardConfig: buildGuardConfig(raw),
      confirmFullAuto: raw.executionMode === 'FULL_AUTO' ? raw.confirmFullAuto : undefined,
    };
    this.saveMutation.mutate(req);
  }

  cancel(): void {
    this.navigateToList();
  }

  hasError(path: string, error: string): boolean {
    const ctrl = this.form.get(path);
    return !!ctrl && ctrl.hasError(error) && (ctrl.touched || this.submitted());
  }

  isInvalid(path: string): boolean {
    const ctrl = this.form.get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }

  collectValidationErrors(): string[] {
    return collectPolicyValidationErrors(this.form, this.translate);
  }

  private scrollToFirstInvalidControl(): void {
    const host: HTMLElement = this.el.nativeElement;
    const invalid = host.querySelector('.ng-invalid:not(form):not([formGroupName])');
    if (invalid) {
      (invalid as HTMLElement).scrollIntoView({ behavior: 'smooth', block: 'center' });
      (invalid as HTMLElement).focus?.();
    }
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
    this.formDirty.set(false);
    this.formPristineAfterPatch = true;
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
