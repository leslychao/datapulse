import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ColDef, GetRowIdParams, ValueFormatterParams } from 'ag-grid-community';

import { CompetitorApiService } from '@core/api/competitor-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import {
  CompetitorMatch,
  CompetitorObservation,
  CreateCompetitorMatchRequest,
  CreateCompetitorObservationRequest,
  TrustLevel,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-competitors-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    FormsModule,
    TranslatePipe,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'pricing.competitors.title' | translate }}
        </h2>
        @if (rbac.canWritePolicies()) {
          <div class="flex gap-2">
            <label
              class="flex h-8 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-secondary)]"
            >
              <input type="file" accept=".csv" (change)="onCsvSelected($event)" class="hidden" />
              {{ 'pricing.competitors.upload_csv' | translate }}
            </label>
            <button
              (click)="showCreateForm.set(true)"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
            >
              {{ 'pricing.competitors.add_match' | translate }}
            </button>
          </div>
        }
      </div>

      <!-- Create form -->
      @if (showCreateForm()) {
        <div class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-4">
          <div class="flex flex-wrap items-end gap-4">
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.competitors.form.offer_id' | translate }}
              </label>
              <input type="number" [(ngModel)]="formOfferId"
                class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="12345" />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.competitors.form.competitor_name' | translate }}
              </label>
              <input type="text" [(ngModel)]="formCompetitorName"
                class="h-8 w-48 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]" />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.competitors.form.competitor_url' | translate }}
              </label>
              <input type="text" [(ngModel)]="formUrl"
                class="h-8 w-64 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]" />
            </div>
            <div class="flex gap-2">
              <button (click)="submitCreate()" [disabled]="!isCreateValid()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50">
                {{ 'common.save' | translate }}
              </button>
              <button (click)="showCreateForm.set(false)"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]">
                {{ 'common.cancel' | translate }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Observation panel -->
      @if (selectedMatch()) {
        <div class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3">
          <div class="mb-2 flex items-center justify-between">
            <h3 class="text-sm font-medium text-[var(--text-primary)]">
              {{ 'pricing.competitors.observations_title' | translate }}
              — {{ selectedMatch()!.competitorName }}
            </h3>
            <button (click)="selectedMatch.set(null)"
              class="cursor-pointer text-xs text-[var(--text-tertiary)] hover:text-[var(--text-primary)]">✕</button>
          </div>

          <!-- Add observation -->
          @if (rbac.canWritePolicies()) {
            <div class="mb-3 flex items-end gap-3">
              <div class="flex flex-col gap-1">
                <label class="text-[11px] text-[var(--text-tertiary)]">
                  {{ 'pricing.competitors.form.price' | translate }}
                </label>
                <input type="number" [(ngModel)]="formObsPrice" min="0.01" step="0.01"
                  class="h-7 w-32 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]" />
              </div>
              <button (click)="submitObservation()" [disabled]="!formObsPrice"
                class="h-7 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 text-xs font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50">
                {{ 'pricing.competitors.update_price' | translate }}
              </button>
            </div>
          }

          <!-- Observations list -->
          @if (obsQuery.isPending()) {
            <p class="text-xs text-[var(--text-tertiary)]">{{ 'common.loading' | translate }}</p>
          } @else if (observations().length === 0) {
            <p class="text-xs text-[var(--text-tertiary)]">{{ 'pricing.competitors.observations_empty' | translate }}</p>
          } @else {
            <div class="max-h-48 overflow-auto rounded-[var(--radius-md)] border border-[var(--border-default)]">
              <table class="w-full text-left text-sm">
                <thead class="bg-[var(--bg-tertiary)] text-xs text-[var(--text-secondary)]">
                  <tr>
                    <th class="px-3 py-1.5">{{ 'pricing.competitors.col.last_price' | translate }}</th>
                    <th class="px-3 py-1.5">{{ 'pricing.competitors.col.observed_at' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (obs of observations(); track obs.id) {
                    <tr class="border-t border-[var(--border-default)]">
                      <td class="px-3 py-1.5 font-mono">{{ obs.competitorPrice | number:'1.2-2' }} ₽</td>
                      <td class="px-3 py-1.5 text-[var(--text-secondary)]">{{ obs.observedAt }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <!-- Grid -->
      <div class="flex-1 px-4 py-2">
        @if (matchesQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.competitors.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="matchesQuery.refetch()"
          />
        } @else if (!matchesQuery.isPending() && rows().length === 0) {
          <dp-empty-state [message]="'pricing.competitors.empty' | translate" />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="matchesQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            (rowClicked)="onRowClicked($event)"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showDeleteModal()"
      [title]="'pricing.competitors.delete_title' | translate"
      [message]="deleteMessage()"
      [confirmLabel]="'pricing.competitors.delete_confirm' | translate"
      (confirmed)="executeDelete()"
      (cancelled)="showDeleteModal.set(false)"
    />
  `,
})
export class CompetitorsPageComponent {
  private readonly api = inject(CompetitorApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);

  readonly wsId = computed(() => this.wsStore.currentWorkspaceId()!);

  readonly showCreateForm = signal(false);
  readonly showDeleteModal = signal(false);
  readonly deleteTarget = signal<CompetitorMatch | null>(null);
  readonly selectedMatch = signal<CompetitorMatch | null>(null);

  formOfferId: number | null = null;
  formCompetitorName = '';
  formUrl = '';
  formObsPrice: number | null = null;

  readonly matchesQuery = injectQuery(() => ({
    queryKey: ['competitors', 'matches', this.wsId()],
    queryFn: () => lastValueFrom(this.api.listMatches(this.wsId())),
    enabled: !!this.wsId(),
  }));

  readonly obsQuery = injectQuery(() => ({
    queryKey: ['competitors', 'observations', this.selectedMatch()?.id],
    queryFn: () =>
      lastValueFrom(
        this.api.listObservations(this.wsId(), this.selectedMatch()!.id),
      ),
    enabled: !!this.selectedMatch(),
  }));

  readonly rows = computed<CompetitorMatch[]>(() => this.matchesQuery.data() ?? []);
  readonly observations = computed<CompetitorObservation[]>(() => this.obsQuery.data() ?? []);

  readonly deleteMessage = computed(() =>
    this.translate.instant('pricing.competitors.delete_message', {
      name: this.deleteTarget()?.competitorName ?? '',
    }),
  );

  readonly columnDefs = computed<ColDef<CompetitorMatch>[]>(() => [
    {
      field: 'marketplaceOfferId',
      headerName: this.translate.instant('pricing.competitors.col.sku'),
      width: 120,
      cellClass: 'font-mono',
    },
    {
      field: 'competitorName',
      headerName: this.translate.instant('pricing.competitors.col.competitor'),
      flex: 1,
      minWidth: 180,
    },
    {
      field: 'trustLevel',
      headerName: this.translate.instant('pricing.competitors.col.trust_level'),
      width: 130,
      valueFormatter: (p: ValueFormatterParams<CompetitorMatch>) =>
        this.translate.instant(`pricing.competitors.trust.${p.value ?? 'TRUSTED'}`),
    },
    {
      field: 'matchMethod',
      headerName: 'Match',
      width: 100,
    },
    {
      field: 'createdAt',
      headerName: this.translate.instant('pricing.competitors.col.observed_at'),
      width: 170,
      valueFormatter: (p: ValueFormatterParams<CompetitorMatch>) =>
        p.value ? formatDateTime(p.value) : '',
    },
    ...(this.rbac.canWritePolicies()
      ? [
          {
            headerName: '',
            width: 80,
            cellRenderer: () =>
              `<button class="text-xs text-[var(--status-error)] hover:underline">✕</button>`,
            onCellClicked: (e: { data: CompetitorMatch }) => {
              this.deleteTarget.set(e.data);
              this.showDeleteModal.set(true);
            },
          } as ColDef<CompetitorMatch>,
        ]
      : []),
  ]);

  readonly getRowId = (params: GetRowIdParams<CompetitorMatch>) =>
    String(params.data.id);

  readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateCompetitorMatchRequest) =>
      lastValueFrom(this.api.createMatch(this.wsId(), req)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['competitors', 'matches'] });
      this.toast.success(this.translate.instant('pricing.competitors.match_created'));
      this.showCreateForm.set(false);
      this.resetForm();
    },
    onError: () =>
      this.toast.error(this.translate.instant('pricing.competitors.match_create_error')),
  }));

  readonly deleteMutation = injectMutation(() => ({
    mutationFn: (matchId: number) =>
      lastValueFrom(this.api.deleteMatch(this.wsId(), matchId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['competitors', 'matches'] });
      this.toast.success(this.translate.instant('pricing.competitors.match_deleted'));
      this.showDeleteModal.set(false);
      if (this.selectedMatch()?.id === this.deleteTarget()?.id) {
        this.selectedMatch.set(null);
      }
    },
    onError: () =>
      this.toast.error(this.translate.instant('pricing.competitors.match_delete_error')),
  }));

  readonly obsMutation = injectMutation(() => ({
    mutationFn: (req: CreateCompetitorObservationRequest) =>
      lastValueFrom(
        this.api.addObservation(this.wsId(), this.selectedMatch()!.id, req),
      ),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['competitors', 'observations'] });
      this.queryClient.invalidateQueries({ queryKey: ['competitors', 'matches'] });
      this.toast.success(this.translate.instant('pricing.competitors.observation_created'));
      this.formObsPrice = null;
    },
    onError: () =>
      this.toast.error(
        this.translate.instant('pricing.competitors.observation_create_error'),
      ),
  }));

  readonly uploadMutation = injectMutation(() => ({
    mutationFn: (file: File) =>
      lastValueFrom(this.api.bulkUpload(this.wsId(), file)),
    onSuccess: (result) => {
      this.queryClient.invalidateQueries({ queryKey: ['competitors', 'matches'] });
      this.toast.success(
        this.translate.instant('pricing.competitors.upload_success', {
          matches: String(result.created),
          observations: String(result.totalRows),
        }),
      );
    },
    onError: () =>
      this.toast.error(this.translate.instant('pricing.competitors.upload_error')),
  }));

  isCreateValid(): boolean {
    return !!this.formOfferId && !!this.formCompetitorName;
  }

  submitCreate(): void {
    if (!this.isCreateValid()) return;
    this.createMutation.mutate({
      marketplaceOfferId: this.formOfferId!,
      competitorName: this.formCompetitorName,
      competitorListingUrl: this.formUrl || undefined,
    });
  }

  submitObservation(): void {
    if (!this.formObsPrice || !this.selectedMatch()) return;
    this.obsMutation.mutate({ competitorPrice: this.formObsPrice });
  }

  onCsvSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.uploadMutation.mutate(file);
      input.value = '';
    }
  }

  onRowClicked(data: CompetitorMatch): void {
    this.selectedMatch.set(data);
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (target) {
      this.deleteMutation.mutate(target.id);
    }
  }

  private resetForm(): void {
    this.formOfferId = null;
    this.formCompetitorName = '';
    this.formUrl = '';
  }
}
