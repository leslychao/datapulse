import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'dp-settings-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="p-4">
      <p class="text-sm text-[var(--text-secondary)]">Настройки — в разработке</p>
      <router-outlet />
    </div>
  `,
})
export class SettingsLayoutComponent {}
