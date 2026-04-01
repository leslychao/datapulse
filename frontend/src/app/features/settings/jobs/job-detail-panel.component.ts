import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, X, RotateCcw } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { JobApiService } from '@core/api/job-api.service';
import { JobSummary } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent } from '@shared/components/status-badge.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';
import { StatusLabelPipe, StatusColorPipe } from '@shared/pipes/status-label.pipe';

@Component({
  selector: 'dp-job-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LucideAngularModule,
    TranslatePipe,
    StatusBadgeComponent,
    SpinnerComponent,
    DateFormatPipe,
    StatusLabelPipe,
    StatusColorPipe,
  ],
  template: `
    <div class="fixed inset-0 z-40 flex justify-end">
      <div class="absolute inset-0 bg-black/20" (click)="closed.emit()"></div>

      <div class="relative z-50 flex w-[560px] flex-col border-l border-[var(--border-default)] bg-[var(--bg-primary)] shadow-lg">
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-[var(--border-default)] px-5 py-3">
          <h2 class="text-sm font-semibold text-[var(--text-primary)]">
            {{ 'settings.jobs.detail_title' | translate }} #{{ jobId() }}
          </h2>
          <div class="flex items-center gap-2">
            @if (jobQuery.data(); as job) {
              @if (canRetry(job)) {
                <button
                  (click)="retry()"
                  [disabled]="retryMutation.isPending()"
                  class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-[var(--text-xs)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50">
                  <lucide-icon [img]="RetryIcon" [size]="13" />
                  {{ 'settings.jobs.retry' | translate }}
                </button>
              }
            }
            <button
              (click)="closed.emit()"
              class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              aria-label="Close">
              <lucide-icon [img]="XIcon" [size]="16" />
            </button>
          </div>
        </div>

        <!-- Body -->
        <div class="flex-1 overflow-auto px-5 py-4">
          @if (jobQuery.isPending()) {
            <dp-spinner [message]="'common.loading' | translate" />
          }

          @if (jobQuery.data(); as job) {
            <!-- Job meta -->
            <div class="mb-5 grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.col_status' | translate }}
                </span>
                <dp-status-badge
                  [label]="job.status | dpStatusLabel"
                  [color]="job.status | dpStatusColor"
                />
              </div>
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.col_event_type' | translate }}
                </span>
                <span class="text-[var(--text-primary)]">
                  {{ 'etl.event_type.' + job.eventType.toLowerCase() | translate }}
                </span>
              </div>
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.col_started' | translate }}
                </span>
                <span class="text-[var(--text-primary)]">{{ job.startedAt | dpDateFormat }}</span>
              </div>
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.col_completed' | translate }}
                </span>
                <span class="text-[var(--text-primary)]">{{ job.completedAt | dpDateFormat }}</span>
              </div>
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.col_created' | translate }}
                </span>
                <span class="text-[var(--text-primary)]">{{ job.createdAt | dpDateFormat }}</span>
              </div>
              <div>
                <span class="block text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.connection_id' | translate }}
                </span>
                <span class="text-[var(--text-primary)]">#{{ job.connectionId }}</span>
              </div>
            </div>

            <!-- Error details -->
            @if (job.errorDetails) {
              <div class="mb-5">
                <span class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.error_details' | translate }}
                </span>
                <pre class="max-h-40 overflow-auto rounded-[var(--radius-md)] bg-[var(--bg-tertiary)] p-3 font-mono text-[var(--text-xs)] text-[var(--status-error)]">{{ formatError(job.errorDetails) }}</pre>
              </div>
            }

            <!-- Items -->
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-sm font-medium text-[var(--text-primary)]">
                {{ 'settings.jobs.items_title' | translate }}
              </h3>
              @if (itemsQuery.data(); as items) {
                <span class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ items.length }}
                </span>
              }
            </div>

            @if (itemsQuery.isPending()) {
              <dp-spinner [message]="'common.loading' | translate" />
            }

            @if (itemsQuery.data(); as items) {
              @if (items.length === 0) {
                <p class="py-4 text-center text-sm text-[var(--text-tertiary)]">
                  {{ 'settings.jobs.no_items' | translate }}
                </p>
              } @else {
                <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
                  <table class="w-full text-[var(--text-xs)]">
                    <thead>
                      <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                        <th class="px-3 py-1.5 text-left font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_source' | translate }}
                        </th>
                        <th class="px-3 py-1.5 text-left font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_status' | translate }}
                        </th>
                        <th class="px-3 py-1.5 text-right font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_page' | translate }}
                        </th>
                        <th class="px-3 py-1.5 text-right font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_records' | translate }}
                        </th>
                        <th class="px-3 py-1.5 text-right font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_size' | translate }}
                        </th>
                        <th class="px-3 py-1.5 text-left font-medium text-[var(--text-secondary)]">
                          {{ 'settings.jobs.item_captured' | translate }}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (item of items; track item.id) {
                        <tr class="border-b border-[var(--border-subtle)]">
                          <td class="px-3 py-1.5 font-mono text-[var(--text-primary)]">
                            {{ item.sourceId }}
                          </td>
                          <td class="px-3 py-1.5">
                            <dp-status-badge
                              [label]="item.status | dpStatusLabel"
                              [color]="item.status | dpStatusColor"
                              [dot]="false"
                            />
                          </td>
                          <td class="px-3 py-1.5 text-right font-mono text-[var(--text-secondary)]">
                            {{ item.pageNumber }}
                          </td>
                          <td class="px-3 py-1.5 text-right font-mono text-[var(--text-secondary)]">
                            {{ item.recordCount ?? '—' }}
                          </td>
                          <td class="px-3 py-1.5 text-right font-mono text-[var(--text-secondary)]">
                            {{ formatBytes(item.byteSize) }}
                          </td>
                          <td class="px-3 py-1.5 text-[var(--text-secondary)]">
                            {{ item.capturedAt | dpDateFormat }}
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            }
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: contents; }
  `],
})
export class JobDetailPanelComponent {
  readonly jobId = input.required<number>();
  readonly closed = output<void>();
  readonly retried = output<void>();

  protected readonly XIcon = X;
  protected readonly RetryIcon = RotateCcw;

  private readonly jobApi = inject(JobApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly jobQuery = injectQuery(() => ({
    queryKey: ['job', this.jobId()],
    queryFn: () => lastValueFrom(this.jobApi.getJob(this.jobId())),
  }));

  readonly itemsQuery = injectQuery(() => ({
    queryKey: ['job-items', this.jobId()],
    queryFn: () => lastValueFrom(this.jobApi.listJobItems(this.jobId())),
  }));

  readonly retryMutation = injectMutation(() => ({
    mutationFn: (jobId: number) => lastValueFrom(this.jobApi.retryJob(jobId)),
    onSuccess: () => {
      this.jobQuery.refetch();
      this.itemsQuery.refetch();
      this.retried.emit();
      this.toast.success(this.translate.instant('settings.jobs.retry_success'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.jobs.retry_error')),
  }));

  canRetry(job: JobSummary): boolean {
    return job.status === 'FAILED' || job.status === 'COMPLETED_WITH_ERRORS';
  }

  retry(): void {
    this.retryMutation.mutate(this.jobId());
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  formatError(details: unknown): string {
    if (typeof details === 'string') return details;
    return JSON.stringify(details, null, 2);
  }
}
