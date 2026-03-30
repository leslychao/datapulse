import { Component } from '@angular/core';

@Component({
  selector: 'dp-dashboard',
  standalone: true,
  template: `
    <div class="p-6">
      <h1 class="text-lg font-semibold text-[var(--text-primary)]">Дашборд</h1>
      <p class="mt-2 text-[var(--text-secondary)]">Здесь будет сводка по маркетплейсам.</p>
    </div>
  `,
})
export class DashboardComponent {}
