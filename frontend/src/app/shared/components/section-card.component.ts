import { Component, Input } from '@angular/core';

@Component({
  selector: 'dp-section-card',
  standalone: true,
  template: `
    <section class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]">
      @if (title) {
        <div class="border-b border-[var(--border-default)] px-5 py-3">
          <h3 class="text-sm font-semibold text-[var(--text-primary)]">{{ title }}</h3>
        </div>
      }
      <div class="px-5 py-4">
        <ng-content />
      </div>
    </section>
  `,
})
export class SectionCardComponent {
  @Input() title = '';
}
