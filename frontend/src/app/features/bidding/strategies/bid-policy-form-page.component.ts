import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  ArrowLeft,
  Eye,
  Clock,
  Zap,
} from 'lucide-angular';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { translateApiErrorMessage } from '@core/i18n/translate-api-error';
import {
  BidPolicyDetail,
  BiddingExecutionMode,
  BiddingStrategyType,
  CreateBidPolicyRequest,
  UpdateBidPolicyRequest,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

import { BidPolicyAssignmentsSectionComponent } from './bid-policy-assignments-section.component';
import { EconomyHoldConfigFormComponent } from './economy-hold-config-form.component';
import { MinimalPresenceConfigFormComponent } from './minimal-presence-config-form.component';
import { GrowthConfigFormComponent } from './growth-config-form.component';
import { PositionHoldConfigFormComponent } from './position-hold-config-form.component';
import { LaunchConfigFormComponent } from './launch-config-form.component';
import { LiquidationConfigFormComponent } from './liquidation-config-form.component';

interface SectionDef {
  id: string;
  labelKey: string;
  editOnly?: boolean;
}

@Component({
  selector: 'dp-bid-policy-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    BidPolicyAssignmentsSectionComponent,
    EconomyHoldConfigFormComponent,
    MinimalPresenceConfigFormComponent,
    GrowthConfigFormComponent,
    PositionHoldConfigFormComponent,
    LaunchConfigFormComponent,
    LiquidationConfigFormComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0 overflow-auto bg-[var(--bg-secondary)]' },
  template: `
    <div class="mx-auto flex w-full max-w-5xl gap-6 px-6 py-6">
      <!-- Sidebar outline (lg+ only) -->
      <nav class="hidden lg:block w-48 shrink-0 sticky top-6 self-start">
        <ul class="space-y-1">
          @for (sec of visibleSections(); track sec.id) {
            <li>
              <button
                type="button"
                (click)="scrollToSection(sec.id)"
                class="w-full cursor-pointer rounded-[var(--radius-sm)] px-3 py-1.5 text-left text-[var(--text-xs)] transition-colors"
                [class]="activeSection() === sec.id
                  ? 'bg-[var(--accent-subtle)] font-medium text-[var(--accent-primary)]'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
              >
                {{ sec.labelKey | translate }}
              </button>
            </li>
          }
        </ul>
      </nav>

      <!-- Form content -->
      <div class="min-w-0 flex-1 space-y-6">
        <!-- Header -->
        <div class="flex items-center gap-3">
          <button
            type="button"
            (click)="cancel()"
            class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-[var(--radius-md)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="icons.ArrowLeft" [size]="18" />
          </button>
          <h1 class="text-lg font-semibold text-[var(--text-primary)]">
            {{ (isEditMode() ? 'bidding.form.title_edit' : 'bidding.form.title_create') | translate }}
          </h1>
        </div>

        <form [formGroup]="form" (ngSubmit)="submit()">
          <!-- Section: Basics -->
          <section id="section-basics"
            class="scroll-mt-4 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 mb-6"
          >
            <h2 class="mb-4 text-[var(--text-sm)] font-semibold text-[var(--text-primary)]">
              {{ 'bidding.form.section.general' | translate }}
            </h2>

            <!-- Name -->
            <div class="mb-4">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'bidding.form.name_label' | translate }}
              </label>
              <input
                type="text"
                formControlName="name"
                [placeholder]="'bidding.form.name_placeholder' | translate"
                class="h-9 w-full rounded-[var(--radius-sm)] border bg-[var(--bg-primary)] px-3 text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
                [class]="isInvalid('name') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)]'"
              />
            </div>

            <!-- Strategy type -->
            <div class="mb-4">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'bidding.form.strategy_type_label' | translate }}
              </label>
              <div class="grid grid-cols-3 gap-3">
                @for (st of strategyTypes; track st.value) {
                  <label
                    class="relative flex cursor-pointer items-start gap-3 rounded-[var(--radius-md)] border p-3 transition-all"
                    [class]="form.get('strategyType')!.value === st.value
                      ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[0_0_0_1px_var(--accent-primary)]'
                      : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)]/40'"
                  >
                    <input
                      type="radio"
                      formControlName="strategyType"
                      [value]="st.value"
                      class="sr-only"
                      [attr.disabled]="isEditMode() ? '' : null"
                    />
                    <div class="min-w-0 flex-1">
                      <span class="block text-[var(--text-sm)] font-medium text-[var(--text-primary)]">
                        {{ st.labelKey | translate }}
                      </span>
                      <span class="block mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
                        {{ st.descKey | translate }}
                      </span>
                    </div>
                  </label>
                }
              </div>
            </div>

            <!-- Execution mode -->
            <div>
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'bidding.form.execution_mode_label' | translate }}
              </label>
              <div class="grid grid-cols-3 gap-3">
                @for (mode of executionModes; track mode.value) {
                  <label
                    class="relative flex cursor-pointer items-center gap-2.5 rounded-[var(--radius-md)] border p-3 transition-all"
                    [class]="form.get('executionMode')!.value === mode.value
                      ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[0_0_0_1px_var(--accent-primary)]'
                      : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)]/40'"
                  >
                    <input
                      type="radio"
                      formControlName="executionMode"
                      [value]="mode.value"
                      class="sr-only"
                    />
                    <lucide-icon [img]="mode.icon" [size]="16"
                      [class]="form.get('executionMode')!.value === mode.value
                        ? 'text-[var(--accent-primary)]'
                        : 'text-[var(--text-tertiary)]'"
                    />
                    <div class="min-w-0 flex-1">
                      <span class="block text-[var(--text-xs)] font-medium"
                        [class]="form.get('executionMode')!.value === mode.value
                          ? 'text-[var(--accent-primary)]'
                          : 'text-[var(--text-primary)]'"
                      >{{ mode.labelKey | translate }}</span>
                      <span class="block text-[10px] leading-tight text-[var(--text-tertiary)]">
                        {{ mode.descKey | translate }}
                      </span>
                    </div>
                  </label>
                }
              </div>
            </div>
          </section>

          <!-- Section: Strategy params -->
          <section id="section-strategy-params"
            class="scroll-mt-4 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 mb-6"
          >
            <h2 class="mb-4 text-[var(--text-sm)] font-semibold text-[var(--text-primary)]">
              {{ 'bidding.form.section.strategy_params' | translate }}
            </h2>
            @switch (form.get('strategyType')?.value) {
              @case ('ECONOMY_HOLD') {
                <dp-economy-hold-config-form
                  [parentForm]="configGroup"
                  [submitted]="submitted()"
                />
              }
              @case ('MINIMAL_PRESENCE') {
                <dp-minimal-presence-config-form />
              }
              @case ('GROWTH') {
                <dp-growth-config-form
                  [parentForm]="configGroup"
                  [submitted]="submitted()"
                />
              }
              @case ('POSITION_HOLD') {
                <dp-position-hold-config-form
                  [parentForm]="configGroup"
                  [submitted]="submitted()"
                />
              }
              @case ('LAUNCH') {
                <dp-launch-config-form
                  [parentForm]="configGroup"
                  [submitted]="submitted()"
                />
              }
              @case ('LIQUIDATION') {
                <dp-liquidation-config-form
                  [parentForm]="configGroup"
                  [submitted]="submitted()"
                />
              }
            }
          </section>

          <!-- Section: Assignments (edit only) -->
          @if (isEditMode()) {
            <dp-bid-policy-assignments-section [policyId]="numericPolicyId()" />
          }

          <!-- Action bar -->
          <div class="flex items-center gap-3 pt-2 pb-8">
            <button
              type="submit"
              [disabled]="saveMutation.isPending()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-6 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ 'bidding.form.save' | translate }}
            </button>
            <button
              type="button"
              (click)="cancel()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'bidding.form.cancel' | translate }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class BidPolicyFormPageComponent implements AfterViewInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly el = inject(ElementRef);
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queryClient = inject(QueryClient);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly icons = { ArrowLeft, Eye, Clock, Zap };

  readonly policyId = input<string>();
  readonly isEditMode = computed(() => !!this.policyId());
  readonly numericPolicyId = computed(() => Number(this.policyId()));
  readonly wsId = computed(() => this.wsStore.currentWorkspaceId()!);

  readonly form: FormGroup = this.fb.group({
    name: ['', Validators.required],
    strategyType: ['ECONOMY_HOLD' as BiddingStrategyType],
    executionMode: ['RECOMMENDATION' as BiddingExecutionMode],
  });

  readonly configGroup = this.fb.group({});

  readonly submitted = signal(false);
  readonly formDirty = signal(false);
  private formPristineAfterPatch = false;

  readonly policyQuery = injectQuery(() => ({
    queryKey: ['bid-policy', this.policyId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.getPolicy(this.wsId(), Number(this.policyId())),
      ),
    enabled: this.isEditMode(),
  }));

  readonly saveMutation = injectMutation(() => ({
    mutationFn: (req: CreateBidPolicyRequest | UpdateBidPolicyRequest) => {
      if (this.isEditMode()) {
        return lastValueFrom(
          this.biddingApi.updatePolicy(
            this.wsId(),
            Number(this.policyId()),
            req as UpdateBidPolicyRequest,
          ),
        );
      }
      return lastValueFrom(
        this.biddingApi.createPolicy(this.wsId(), req as CreateBidPolicyRequest),
      );
    },
    onSuccess: (result: BidPolicyDetail) => {
      this.queryClient.invalidateQueries({ queryKey: ['bid-policies'] });
      this.formDirty.set(false);
      if (this.isEditMode()) {
        this.queryClient.invalidateQueries({ queryKey: ['bid-policy', this.policyId()] });
        this.toast.success(this.translate.instant('bidding.policies.updated'));
        this.navigateToList();
      } else {
        this.toast.success(this.translate.instant('bidding.policies.created'));
        this.router.navigate([
          '/workspace', this.wsId(), 'bidding', 'strategies', result.id, 'edit',
        ]);
      }
    },
    onError: (err) =>
      this.toast.error(
        translateApiErrorMessage(this.translate, err, 'bidding.policies.save_error'),
      ),
  }));

  readonly sections: SectionDef[] = [
    { id: 'section-basics', labelKey: 'bidding.form.section.general' },
    { id: 'section-strategy-params', labelKey: 'bidding.form.section.strategy_params' },
    { id: 'section-assignments', labelKey: 'bidding.form.section.assignments', editOnly: true },
  ];

  readonly visibleSections = computed(() =>
    this.sections.filter((s) => !s.editOnly || this.isEditMode()),
  );

  readonly activeSection = signal('section-basics');
  private observer: IntersectionObserver | null = null;

  readonly strategyTypes: {
    value: BiddingStrategyType;
    labelKey: string;
    descKey: string;
  }[] = [
    {
      value: 'ECONOMY_HOLD',
      labelKey: 'bidding.policies.strategy.ECONOMY_HOLD',
      descKey: 'bidding.form.tooltip.strategy_type.ECONOMY_HOLD',
    },
    {
      value: 'MINIMAL_PRESENCE',
      labelKey: 'bidding.policies.strategy.MINIMAL_PRESENCE',
      descKey: 'bidding.form.tooltip.strategy_type.MINIMAL_PRESENCE',
    },
    {
      value: 'GROWTH',
      labelKey: 'bidding.policies.strategy.GROWTH',
      descKey: 'bidding.form.tooltip.strategy_type.GROWTH',
    },
    {
      value: 'POSITION_HOLD',
      labelKey: 'bidding.policies.strategy.POSITION_HOLD',
      descKey: 'bidding.form.tooltip.strategy_type.POSITION_HOLD',
    },
    {
      value: 'LAUNCH',
      labelKey: 'bidding.policies.strategy.LAUNCH',
      descKey: 'bidding.form.tooltip.strategy_type.LAUNCH',
    },
    {
      value: 'LIQUIDATION',
      labelKey: 'bidding.policies.strategy.LIQUIDATION',
      descKey: 'bidding.form.tooltip.strategy_type.LIQUIDATION',
    },
  ];

  readonly executionModes: {
    value: BiddingExecutionMode;
    labelKey: string;
    descKey: string;
    icon: any;
  }[] = [
    {
      value: 'RECOMMENDATION',
      labelKey: 'bidding.policies.mode.RECOMMENDATION',
      descKey: 'bidding.form.tooltip.execution_mode.RECOMMENDATION',
      icon: Eye,
    },
    {
      value: 'SEMI_AUTO',
      labelKey: 'bidding.policies.mode.SEMI_AUTO',
      descKey: 'bidding.form.tooltip.execution_mode.SEMI_AUTO',
      icon: Clock,
    },
    {
      value: 'FULL_AUTO',
      labelKey: 'bidding.policies.mode.FULL_AUTO',
      descKey: 'bidding.form.tooltip.execution_mode.FULL_AUTO',
      icon: Zap,
    },
  ];

  constructor() {
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
    this.configGroup.valueChanges.subscribe(() => {
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
    return confirm(this.translate.instant('bidding.form.unsaved_changes'));
  }

  submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    this.configGroup.markAllAsTouched();

    if (this.form.invalid || this.configGroup.invalid) {
      this.toast.error(this.translate.instant('bidding.form.has_errors'));
      return;
    }

    const raw = this.form.getRawValue();
    const config = this.buildConfig();

    if (this.isEditMode()) {
      const req: UpdateBidPolicyRequest = {
        name: raw.name,
        executionMode: raw.executionMode,
        config,
      };
      this.saveMutation.mutate(req);
    } else {
      const req: CreateBidPolicyRequest = {
        name: raw.name,
        strategyType: raw.strategyType,
        executionMode: raw.executionMode,
        config,
      };
      this.saveMutation.mutate(req);
    }
  }

  private buildConfig(): Record<string, unknown> {
    const strategyType = this.form.get('strategyType')?.value as BiddingStrategyType;
    if (strategyType === 'MINIMAL_PRESENCE') {
      return {};
    }
    const values = this.configGroup.getRawValue();
    const config: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(values)) {
      if (val !== null && val !== '') config[key] = val;
    }
    return config;
  }

  cancel(): void {
    this.navigateToList();
  }

  isInvalid(path: string): boolean {
    const ctrl = this.form.get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }

  scrollToSection(sectionId: string): void {
    const host: HTMLElement = this.el.nativeElement;
    const target = host.querySelector(`#${sectionId}`);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  ngAfterViewInit(): void {
    this.setupSectionObserver();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private setupSectionObserver(): void {
    const host: HTMLElement = this.el.nativeElement;
    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.activeSection.set(entry.target.id);
            break;
          }
        }
      },
      { root: host, rootMargin: '-20% 0px -60% 0px', threshold: 0 },
    );
    for (const section of this.sections) {
      const el = host.querySelector(`#${section.id}`);
      if (el) this.observer.observe(el);
    }
  }

  private patchForm(policy: BidPolicyDetail): void {
    this.form.patchValue({
      name: policy.name,
      strategyType: policy.strategyType,
      executionMode: policy.executionMode,
    });
    if (policy.config) {
      setTimeout(() => {
        this.configGroup.patchValue(policy.config as Record<string, unknown>);
        this.formDirty.set(false);
        this.formPristineAfterPatch = true;
      });
    } else {
      this.formDirty.set(false);
      this.formPristineAfterPatch = true;
    }
  }

  private navigateToList(): void {
    this.router.navigate([
      '/workspace',
      String(this.wsId()),
      'bidding',
      'strategies',
    ]);
  }
}
