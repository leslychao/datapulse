import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { translateApiErrorMessage } from '@core/i18n/translate-api-error';
import { CreatePromoPolicyRequest, ParticipationMode, PromoPolicy } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

const MODES: { value: ParticipationMode; titleKey: string; descKey: string }[] = [
  { value: 'RECOMMENDATION', titleKey: 'promo.mode.recommendation', descKey: 'promo.mode.recommendation_desc' },
  { value: 'SEMI_AUTO', titleKey: 'promo.mode.semi_auto', descKey: 'promo.mode.semi_auto_desc' },
  { value: 'FULL_AUTO', titleKey: 'promo.mode.full_auto', descKey: 'promo.mode.full_auto_desc' },
  { value: 'SIMULATED', titleKey: 'promo.mode.simulated', descKey: 'promo.mode.simulated_desc' },
];

@Component({
  selector: 'dp-promo-policy-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-1 flex-col min-h-0' },
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './promo-policy-form-page.component.html',
})
export class PromoPolicyFormPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);
  private readonly queryClient = inject(QueryClient);

  readonly policyId = input<string>();

  readonly isEditMode = computed(() => !!this.policyId());
  readonly saving = signal(false);
  readonly formDirty = signal(false);
  readonly modes = MODES;

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
    participationMode: ['SEMI_AUTO' as ParticipationMode, Validators.required],
    minMarginPct: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
    minStockDaysOfCover: [7, [Validators.required, Validators.min(1)]],
    maxPromoDiscountPct: [null as number | null, [Validators.min(0), Validators.max(100)]],
    autoParticipateCategories: [''],
    autoDeclineCategories: [''],
  });

  readonly policyQuery = injectQuery(() => ({
    queryKey: ['promo-policy', this.wsStore.currentWorkspaceId(), this.policyId()],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.getPolicy(
          this.wsStore.currentWorkspaceId()!,
          Number(this.policyId()),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && this.isEditMode(),
  }));

  constructor() {
    effect(() => {
      const data = this.policyQuery.data();
      if (data && this.isEditMode()) {
        this.patchForm(data);
      }
    });
  }

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreatePromoPolicyRequest) =>
      lastValueFrom(this.promoApi.createPolicy(this.wsStore.currentWorkspaceId()!, req)),
    onSuccess: () => {
      this.formDirty.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success(this.translate.instant('promo.form.toast.created'));
      this.navigateBack();
    },
    onError: (err) => {
      this.saving.set(false);
      this.toast.error(
        translateApiErrorMessage(this.translate, err, 'promo.form.toast.create_error'),
      );
    },
  }));

  private readonly createAndActivateMutation = injectMutation(() => ({
    mutationFn: async (req: CreatePromoPolicyRequest) => {
      const policy = await lastValueFrom(
        this.promoApi.createPolicy(this.wsStore.currentWorkspaceId()!, req),
      );
      await lastValueFrom(
        this.promoApi.activatePolicy(this.wsStore.currentWorkspaceId()!, policy.id),
      );
      return policy;
    },
    onSuccess: () => {
      this.formDirty.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success(this.translate.instant('promo.form.toast.activated'));
      this.navigateBack();
    },
    onError: (err) => {
      this.saving.set(false);
      this.toast.error(
        translateApiErrorMessage(this.translate, err, 'promo.form.toast.create_error'),
      );
    },
  }));

  private readonly updateMutation = injectMutation(() => ({
    mutationFn: (req: CreatePromoPolicyRequest) =>
      lastValueFrom(
        this.promoApi.updatePolicy(
          this.wsStore.currentWorkspaceId()!,
          Number(this.policyId()),
          req,
        ),
      ),
    onSuccess: () => {
      this.formDirty.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-policy'] });
      this.toast.success(this.translate.instant('promo.form.toast.updated'));
      this.navigateBack();
    },
    onError: (err) => {
      this.saving.set(false);
      this.toast.error(
        translateApiErrorMessage(this.translate, err, 'promo.form.toast.update_error'),
      );
    },
  }));

  private patchForm(data: PromoPolicy): void {
    this.form.patchValue({
      name: data.name,
      participationMode: data.participationMode,
      minMarginPct: data.minMarginPct,
      minStockDaysOfCover: data.minStockDaysOfCover,
      maxPromoDiscountPct: data.maxPromoDiscountPct,
      autoParticipateCategories: data.autoParticipateCategories?.join(', ') ?? '',
      autoDeclineCategories: data.autoDeclineCategories?.join(', ') ?? '',
    });
    this.formDirty.set(false);
  }

  onFormChange(): void {
    this.formDirty.set(true);
  }

  selectMode(mode: ParticipationMode): void {
    this.form.patchValue({ participationMode: mode });
    this.formDirty.set(true);
  }

  private buildRequest(): CreatePromoPolicyRequest {
    const val = this.form.getRawValue();
    const parseCategories = (str: string | null): number[] | null => {
      if (!str?.trim()) return null;
      return str
        .split(',')
        .map((s) => parseInt(s.trim(), 10))
        .filter((n) => !isNaN(n));
    };
    return {
      name: val.name!,
      participationMode: val.participationMode!,
      minMarginPct: val.minMarginPct!,
      minStockDaysOfCover: val.minStockDaysOfCover!,
      maxPromoDiscountPct: val.maxPromoDiscountPct,
      autoParticipateCategories: parseCategories(val.autoParticipateCategories),
      autoDeclineCategories: parseCategories(val.autoDeclineCategories),
      evaluationConfig: null,
    };
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.warning(this.translate.instant('promo.form.validation_required'));
      return;
    }

    this.saving.set(true);
    const req = this.buildRequest();
    if (this.isEditMode()) {
      this.updateMutation.mutate(req);
    } else {
      this.createMutation.mutate(req);
    }
  }

  submitAndActivate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.warning(this.translate.instant('promo.form.validation_required'));
      return;
    }

    this.saving.set(true);
    this.createAndActivateMutation.mutate(this.buildRequest());
  }

  canDeactivate(): boolean {
    return !this.formDirty();
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'policies']);
  }
}
