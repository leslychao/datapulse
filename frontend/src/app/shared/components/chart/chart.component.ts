import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  DataZoomComponent,
  MarkLineComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsOption } from 'echarts';

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  DataZoomComponent,
  MarkLineComponent,
  CanvasRenderer,
]);

@Component({
  selector: 'dp-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts })],
  template: `
    @if (loading()) {
      <div
        class="dp-shimmer rounded-[var(--radius-md)]"
        [style.height]="height()"
      ></div>
    } @else {
      <div
        echarts
        [options]="options()"
        [style.height]="height()"
        class="w-full"
      ></div>
    }
  `,
})
export class ChartComponent {
  readonly options = input.required<EChartsOption>();
  readonly height = input<string>('300px');
  readonly loading = input<boolean>(false);
}
