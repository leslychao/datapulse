import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { CreatePromoPolicyRequest, ParticipationMode } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

const MODES: { value: ParticipationMode; title: string; description: string }[] = [
  { value: 'RECOMMENDATION', title: 'Рекомендация', description: 'Показывает рекомендацию, оператор решает' },
  { value: 'SEMI_AUTO', title: 'Полу-авто', description: 'Создаёт действие, ожидает одобрения' },
  { value: 'FULL_AUTO', title: 'Полный авто', description: 'Автоматическое участие через guards' },
  { value: 'SIMULATED', title: 'Симуляция', description: 'Имитация без реального вызова API' },
];

@Component({
  selector: 'dp-promo-policy-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './promo-policy-form-page.component.html',
})
export class PromoPolicyFormPageComponent implements OnInit {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  private readonly queryClient = inject(QueryClient);

  readonly policyId = input<string>();

  readonly isEditMode = computed(() => !!this.policyId());
  readonly saving = signal(false);
  readonly modes = MODES;

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
    participationMode: ['SEMI_AUTO' as ParticipationMode, Validators.required],
    minMarginPct: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
    minStockDaysOfCover: [7, [Validators.required, Validators.min(1)]],
    maxPromoDiscountPct: [null as number | null, [Validators.min(0), Validators.max(100)]],
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

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreatePromoPolicyRequest) =>
      lastValueFrom(this.promoApi.createPolicy(this.wsStore.currentWorkspaceId()!, req)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success('Промо-политика создана');
      this.navigateBack();
    },
    onError: () => this.toast.error('Не удалось создать промо-политику'),
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
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-policy'] });
      this.toast.success('Промо-политика обновлена');
      this.navigateBack();
    },
    onError: () => this.toast.error('Не удалось обновить промо-политику'),
  }));

  ngOnInit(): void {
    if (this.isEditMode()) {
      const data = this.policyQuery.data();
      if (data) {
        this.patchForm(data);
      }
    }
  }

  private patchForm(data: any): void {
    this.form.patchValue({
      name: data.name,
      participationMode: data.participationMode,
      minMarginPct: data.minMarginPct,
      minStockDaysOfCover: data.minStockDaysOfCover,
      maxPromoDiscountPct: data.maxPromoDiscountPct,
    });
  }

  selectMode(mode: ParticipationMode): void {
    this.form.patchValue({ participationMode: mode });
  }

  submit(): void {
    if (this.form.invalid) return;

    const val = this.form.getRawValue();
    const req: CreatePromoPolicyRequest = {
      name: val.name!,
      participationMode: val.participationMode!,
      minMarginPct: val.minMarginPct!,
      minStockDaysOfCover: val.minStockDaysOfCover!,
      maxPromoDiscountPct: val.maxPromoDiscountPct,
      autoParticipateCategories: null,
      autoDeclineCategories: null,
      evaluationConfig: null,
    };

    this.saving.set(true);
    if (this.isEditMode()) {
      this.updateMutation.mutate(req);
    } else {
      this.createMutation.mutate(req);
    }
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'policies']);
  }
}
