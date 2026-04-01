import { ChangeDetectionStrategy, Component, ElementRef, effect, input, viewChild } from '@angular/core';

@Component({
  selector: 'dp-sparkline-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas [width]="width()" [height]="height()" class="block"></canvas>`,
})
export class SparklineChartComponent {
  readonly data = input<number[]>([]);
  readonly width = input(80);
  readonly height = input(24);
  readonly color = input('var(--accent-primary)');

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  constructor() {
    effect(() => {
      const points = this.data();
      const el = this.canvas().nativeElement;
      const ctx = el.getContext('2d');
      if (!ctx || points.length < 2) return;

      const w = el.width;
      const h = el.height;
      const min = Math.min(...points);
      const max = Math.max(...points);
      const range = max - min || 1;

      ctx.clearRect(0, 0, w, h);
      ctx.beginPath();
      ctx.strokeStyle = getComputedStyle(el).getPropertyValue('--accent-primary')?.trim() || '#6366f1';
      ctx.lineWidth = 1.5;
      ctx.lineJoin = 'round';

      points.forEach((val, i) => {
        const x = (i / (points.length - 1)) * w;
        const y = h - ((val - min) / range) * (h - 4) - 2;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });

      ctx.stroke();
    });
  }
}
