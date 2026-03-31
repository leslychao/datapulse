import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'dp-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div
      class="flex min-h-[50vh] flex-col items-center justify-center gap-4 p-4 text-center"
    >
      <p class="text-lg text-[var(--text-primary)]">Страница не найдена</p>
      <a
        routerLink="/workspaces"
        class="text-sm text-[var(--text-secondary)] underline underline-offset-2 hover:text-[var(--text-primary)]"
      >
        На главную
      </a>
    </div>
  `,
})
export class NotFoundComponent {}
