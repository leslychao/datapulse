import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ChevronRight } from 'lucide-angular';

import { BreadcrumbService } from '@shared/services/breadcrumb.service';

@Component({
  selector: 'dp-breadcrumbs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LucideAngularModule],
  template: `
    <nav class="flex items-center gap-1 overflow-hidden">
      @for (segment of breadcrumbService.segments(); track segment.label; let last = $last) {
        @if (!last && segment.route) {
          <a
            [routerLink]="segment.route"
            class="shrink-0 text-sm text-[var(--text-secondary)] transition-colors hover:text-[var(--text-primary)] hover:underline"
          >
            {{ segment.label }}
          </a>
        } @else {
          <span
            class="truncate text-sm"
            [class]="last ? 'text-[var(--text-primary)]' : 'text-[var(--text-secondary)]'"
          >
            {{ segment.label }}
          </span>
        }

        @if (!last) {
          <lucide-icon
            [img]="ChevronRight"
            [size]="12"
            class="shrink-0 text-[var(--text-tertiary)]"
          />
        }
      }
    </nav>
  `,
})
export class BreadcrumbsComponent {
  protected readonly ChevronRight = ChevronRight;
  protected readonly breadcrumbService = inject(BreadcrumbService);
}
