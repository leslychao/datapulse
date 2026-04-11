import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { EChartsOption } from 'echarts';

import { ChartComponent } from '@shared/components/chart/chart.component';

@Component({
  selector: 'dp-mismatch-charts-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ChartComponent],
  template: `
    @if (!collapsed()) {
      <div class="grid grid-cols-[2fr_3fr] gap-3 px-4 py-3">
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-2">
          <dp-chart
            [options]="donutOptions()"
            height="180px"
            [loading]="loading()"
            (chartClick)="chartClick.emit($event)"
          />
        </div>
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-2">
          <dp-chart
            [options]="timelineOptions()"
            height="180px"
            [loading]="loading()"
          />
        </div>
      </div>
    }
  `,
})
export class MismatchChartsSectionComponent {
  readonly collapsed = input(true);
  readonly loading = input(false);
  readonly donutOptions = input<EChartsOption>({});
  readonly timelineOptions = input<EChartsOption>({});
  readonly chartClick = output<Record<string, unknown>>();
}
