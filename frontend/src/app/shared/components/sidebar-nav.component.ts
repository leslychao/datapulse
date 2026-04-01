import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

export interface SidebarSection {
  label: string;
  items: SidebarItem[];
}

export interface SidebarItem {
  label: string;
  route: string;
  badge?: number;
}

@Component({
  selector: 'dp-sidebar-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <nav class="flex w-52 flex-col gap-4 border-r border-[var(--border-default)] bg-[var(--bg-secondary)] p-4">
      @for (section of sections(); track section.label) {
        <div class="flex flex-col gap-0.5">
          <span class="mb-1 text-xs font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">
            {{ section.label | translate }}
          </span>
          @for (item of section.items; track item.route) {
            <a
              [routerLink]="item.route"
              routerLinkActive="bg-[var(--bg-active)] text-[var(--text-primary)] font-medium"
              class="flex items-center justify-between rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ item.label | translate }}
              @if (item.badge) {
                <span class="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-[var(--accent-primary)] px-1.5 text-[11px] font-semibold text-white">
                  {{ item.badge }}
                </span>
              }
            </a>
          }
        </div>
      }
    </nav>
  `,
})
export class SidebarNavComponent {
  readonly sections = input<SidebarSection[]>([]);
}
