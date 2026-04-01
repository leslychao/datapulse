import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div
      class="flex min-h-[50vh] flex-col items-center justify-center gap-4 p-4 text-center"
    >
      <p class="text-lg text-[var(--text-primary)]">{{ 'not_found.title' | translate }}</p>
      <a
        routerLink="/workspaces"
        class="text-sm text-[var(--text-secondary)] underline underline-offset-2 hover:text-[var(--text-primary)]"
      >
        {{ 'not_found.go_home' | translate }}
      </a>
    </div>
  `,
})
export class NotFoundComponent {}
