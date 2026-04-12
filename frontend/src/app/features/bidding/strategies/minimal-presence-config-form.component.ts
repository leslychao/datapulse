import { ChangeDetectionStrategy, Component } from '@angular/core';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-minimal-presence-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="rounded-[var(--radius-md)] border border-[var(--border-subtle)] bg-[var(--bg-secondary)] p-4">
      <p class="text-[var(--text-sm)] text-[var(--text-secondary)]">
        {{ 'bidding.strategy.minimal_presence.description' | translate }}
      </p>
    </div>
  `,
})
export class MinimalPresenceConfigFormComponent {}
