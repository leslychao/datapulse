import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, RefreshCw, ChevronLeft, ChevronRight, RotateCcw, X, Eye } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { JobApiService } from '@core/api/job-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import { ConnectionSummary, JobFilter, JobStatus, JobSummary } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { StatusLabelPipe, StatusColorPipe } from '@shared/pipes/status-label.pipe';
import { JobDetailPanelComponent } from './job-detail-panel.component';

const JOB_STATUSES: JobStatus[] = [
  'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS',
  'RETRY_SCHEDULED', 'FAILED', 'STALE',
];

const PAGE_SIZE = 20;

@Component({
  selector: 'dp-jobs-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    LucideAngularModule,
    TranslatePipe,
    StatusBadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    DateFormatPipe,
    RelativeTimePipe,
    StatusLabelPipe,
    StatusColorPipe,
    JobDetailPanelComponent,
  ],
  template: `
    <div class="max-w-5xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">
          {{ 'settings.jobs.title' | translate }}
        </h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'settings.jobs.subtitle' | translate }}
        </p>
      </div>

      <!-- Filters -->
      <div class="mb-4 flex flex-wrap items-end gap-3">
        <div class="min-w-[180px]">
          <label class="mb-1 block text-[var(--text-xs)] text-[var(--text-secondary)]">
            {{ 'settings.jobs.filter_connection' | translate }}
          </label>
          <select
            [ngModel]="selectedConnectionId()"
            (ngModelChange)="onConnectionChange($event)"
            class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]">
            @if (connectionsQuery.data(); as conns) {
              @for (conn of conns; track conn.id) {
                <option [value]="conn.id">{{ conn.name }}</option>
              }
            }
          </select>
        </div>

        <div class="min-w-[150px]">
          <label class="mb-1 block text-[var(--text-xs)] text-[var(--text-secondary)]">
            {{ 'settings.jobs.filter_status' | translate }}
          </label>
          <select
            [ngModel]="filterStatus()"
            (ngModelChange)="onStatusChange($event)"
            class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]">
            <option value="">{{ 'settings.jobs.filter_all_statuses' | translate }}</option>
            @for (s of statuses; track s) {
              <option [value]="s">{{ 'status.' + s.toLowerCase() | translate }}</option>
            }
          </select>
        </div>

        <div>
          <label class="mb-1 block text-[var(--text-xs)] text-[var(--text-secondary)]">
            {{ 'settings.jobs.filter_from' | translate }}
          </label>
          <input
            type="date"
            [ngModel]="filterFrom()"
            (ngModelChange)="onFromChange($event)"
            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          />
        </div>

        <div>
          <label class="mb-1 block text-[var(--text-xs)] text-[var(--text-secondary)]">
            {{ 'settings.jobs.filter_to' | translate }}
          </label>
          <input
            type="date"
            [ngModel]="filterTo()"
            (ngModelChange)="onToChange($event)"
            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          />
        </div>

        @if (hasActiveFilters()) {
          <button
            (click)="clearFilters()"
            class="flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] px-2 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]">
            <lucide-icon [img]="XIcon" [size]="14" />
            {{ 'settings.jobs.clear_filters' | translate }}
          </button>
        }

        <button
          (click)="jobsQuery.refetch()"
          class="ml-auto flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]">
          <lucide-icon [img]="RefreshIcon" [size]="14" />
          {{ 'common.refresh' | translate }}
        </button>
      </div>

      <!-- Loading -->
      @if (connectionsQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (connectionsQuery.data(); as conns) {
        @if (conns.length === 0) {
          <dp-empty-state
            [message]="'settings.jobs.no_connections' | translate"
            [hint]="'settings.jobs.no_connections_hint' | translate"
          />
        } @else {
          @if (jobsQuery.isPending()) {
            <dp-spinner [message]="'common.loading' | translate" />
          }

          @if (jobsQuery.data(); as page) {
            @if (page.content.length === 0) {
              <dp-empty-state [message]="'settings.jobs.empty' | translate" />
            } @else {
              <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_id' | translate }}
                      </th>
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_event_type' | translate }}
                      </th>
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_status' | translate }}
                      </th>
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_started' | translate }}
                      </th>
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_completed' | translate }}
                      </th>
                      <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">
                        {{ 'settings.jobs.col_created' | translate }}
                      </th>
                      <th class="w-24 px-4 py-2"></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (job of page.content; track job.id) {
                      <tr
                        class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)]"
                        [class.bg-[var(--bg-secondary)]]="selectedJobId() === job.id">
                        <td class="px-4 py-2.5 font-mono text-[var(--text-tertiary)]">
                          #{{ job.id }}
                        </td>
                        <td class="px-4 py-2.5 text-[var(--text-primary)]">
                          {{ 'etl.event_type.' + job.eventType.toLowerCase() | translate }}
                        </td>
                        <td class="px-4 py-2.5">
                          <dp-status-badge
                            [label]="job.status | dpStatusLabel"
                            [color]="job.status | dpStatusColor"
                          />
                        </td>
                        <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                          {{ job.startedAt | dpDateFormat }}
                        </td>
                        <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                          {{ job.completedAt | dpDateFormat }}
                        </td>
                        <td class="px-4 py-2.5 text-[var(--text-secondary)]">
                          {{ job.createdAt | dpRelativeTime }}
                        </td>
                        <td class="px-4 py-2.5 text-right">
                          <div class="flex items-center justify-end gap-1">
                            <button
                              (click)="openDetail(job)"
                              class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
                              [attr.aria-label]="'settings.jobs.view_detail' | translate">
                              <lucide-icon [img]="EyeIcon" [size]="14" />
                            </button>
                            @if (canRetry(job)) {
                              <button
                                (click)="retryJob(job); $event.stopPropagation()"
                                [disabled]="retryMutation.isPending()"
                                class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--accent-primary)] disabled:cursor-not-allowed disabled:opacity-50"
                                [attr.aria-label]="'settings.jobs.retry' | translate">
                                <lucide-icon [img]="RetryIcon" [size]="14" />
                              </button>
                            }
                          </div>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>

              <!-- Pagination -->
              @if (page.totalPages > 1) {
                <div class="mt-3 flex items-center justify-between text-sm text-[var(--text-secondary)]">
                  <span>
                    {{ 'settings.jobs.page_info' | translate:{current: page.number + 1, total: page.totalPages, items: page.totalElements} }}
                  </span>
                  <div class="flex items-center gap-1">
                    <button
                      (click)="goToPage(currentPage() - 1)"
                      [disabled]="currentPage() === 0"
                      class="cursor-pointer rounded p-1 transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30">
                      <lucide-icon [img]="ChevronLeftIcon" [size]="16" />
                    </button>
                    <button
                      (click)="goToPage(currentPage() + 1)"
                      [disabled]="currentPage() >= page.totalPages - 1"
                      class="cursor-pointer rounded p-1 transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-30">
                      <lucide-icon [img]="ChevronRightIcon" [size]="16" />
                    </button>
                  </div>
                </div>
              }
            }
          }
        }
      }
    </div>

    <!-- Detail Panel -->
    @if (selectedJobId(); as jobId) {
      <dp-job-detail-panel
        [jobId]="jobId"
        (closed)="closeDetail()"
        (retried)="onRetried()"
      />
    }
  `,
})
export class JobsPageComponent {
  protected readonly RefreshIcon = RefreshCw;
  protected readonly ChevronLeftIcon = ChevronLeft;
  protected readonly ChevronRightIcon = ChevronRight;
  protected readonly RetryIcon = RotateCcw;
  protected readonly XIcon = X;
  protected readonly EyeIcon = Eye;

  protected readonly statuses = JOB_STATUSES;

  private readonly jobApi = inject(JobApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly selectedConnectionId = signal<number | null>(null);
  readonly filterStatus = signal<string>('');
  readonly filterFrom = signal<string>('');
  readonly filterTo = signal<string>('');
  readonly currentPage = signal(0);
  readonly selectedJobId = signal<number | null>(null);

  readonly hasActiveFilters = computed(() =>
    !!this.filterStatus() || !!this.filterFrom() || !!this.filterTo());

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 60_000,
  }));

  private readonly activeConnectionId = computed(() => {
    const selected = this.selectedConnectionId();
    if (selected) return selected;
    const conns = this.connectionsQuery.data();
    return conns?.length ? conns[0].id : null;
  });

  readonly jobsQuery = injectQuery(() => {
    const connId = this.activeConnectionId();
    const filter: JobFilter = {};
    const statusVal = this.filterStatus();
    if (statusVal) filter.status = statusVal as JobStatus;
    if (this.filterFrom()) filter.from = new Date(this.filterFrom()).toISOString();
    if (this.filterTo()) filter.to = new Date(this.filterTo() + 'T23:59:59').toISOString();

    return {
      queryKey: ['jobs', connId, this.currentPage(), filter],
      queryFn: () => lastValueFrom(
        this.jobApi.listJobs(connId!, filter, this.currentPage(), PAGE_SIZE),
      ),
      enabled: !!connId,
    };
  });

  readonly retryMutation = injectMutation(() => ({
    mutationFn: (jobId: number) => lastValueFrom(this.jobApi.retryJob(jobId)),
    onSuccess: () => {
      this.jobsQuery.refetch();
      this.toast.success(this.translate.instant('settings.jobs.retry_success'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.jobs.retry_error')),
  }));

  onConnectionChange(value: string): void {
    this.selectedConnectionId.set(Number(value));
    this.currentPage.set(0);
  }

  onStatusChange(value: string): void {
    this.filterStatus.set(value);
    this.currentPage.set(0);
  }

  onFromChange(value: string): void {
    this.filterFrom.set(value);
    this.currentPage.set(0);
  }

  onToChange(value: string): void {
    this.filterTo.set(value);
    this.currentPage.set(0);
  }

  clearFilters(): void {
    this.filterStatus.set('');
    this.filterFrom.set('');
    this.filterTo.set('');
    this.currentPage.set(0);
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
  }

  canRetry(job: JobSummary): boolean {
    return job.status === 'FAILED' || job.status === 'COMPLETED_WITH_ERRORS';
  }

  retryJob(job: JobSummary): void {
    this.retryMutation.mutate(job.id);
  }

  openDetail(job: JobSummary): void {
    this.selectedJobId.set(job.id);
  }

  closeDetail(): void {
    this.selectedJobId.set(null);
  }

  onRetried(): void {
    this.jobsQuery.refetch();
  }
}
