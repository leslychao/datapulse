import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { WorkspaceApiService } from '@core/api/workspace-api.service';
import { AuthService } from '@core/auth/auth.service';
import { WorkspaceMembership, WorkspaceRole, WorkspaceDetail, getMarketplaceShortLabel } from '@core/models';
import { MinimalTopBarComponent } from '@shared/layout/minimal-top-bar.component';
import { CenteredContentComponent } from '@shared/layout/centered-content.component';
import { StatusMessageComponent } from '@shared/layout/status-message.component';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

type PageState = 'loading' | 'loaded' | 'empty' | 'error';

interface WorkspaceCard {
  id: number;
  name: string;
  tenantName: string;
  membersCount: number;
  connectionsCount: number;
  role: WorkspaceRole;
  marketplaces: string[];
}

@Component({
  selector: 'dp-workspace-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MinimalTopBarComponent, CenteredContentComponent, StatusMessageComponent, TranslatePipe],
  template: `
    <div class="flex h-screen flex-col bg-[var(--bg-secondary)]">
      <dp-minimal-top-bar
        [userEmail]="userEmail()"
        (logoutClick)="onLogout()"
      />

      @switch (state()) {
        @case ('loading') {
          <dp-centered-content>
            <dp-status-message icon="spinner" [title]="'workspace_selector.loading' | translate" />
          </dp-centered-content>
        }

        @case ('error') {
          <dp-centered-content>
            <dp-status-message
              icon="error"
              [title]="'workspace_selector.error_title' | translate"
              [description]="'workspace_selector.error_description' | translate"
              [actionLabel]="'actions.retry' | translate"
              (actionClick)="loadData()"
            />
          </dp-centered-content>
        }

        @case ('empty') {
          <dp-centered-content>
            <dp-status-message
              icon="info"
              [title]="'workspace_selector.empty_title' | translate"
              [description]="'workspace_selector.empty_description' | translate"
              [actionLabel]="'workspace_selector.create' | translate"
              (actionClick)="onCreateWorkspace()"
            />
          </dp-centered-content>
        }

        @case ('loaded') {
          <dp-centered-content maxWidth="960px">
            <h1 class="mb-6 text-center text-xl font-semibold text-[var(--text-primary)]">
              {{ 'workspace_selector.title' | translate }}
            </h1>

            <div class="grid justify-center gap-4" style="grid-template-columns: repeat(auto-fit, 280px);">
              @for (ws of workspaces(); track ws.id) {
                <button
                  (click)="onSelectWorkspace(ws)"
                  class="flex cursor-pointer flex-col gap-3 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4 text-left shadow-[var(--shadow-sm)] transition-all hover:border-[var(--accent-primary)] hover:shadow-[var(--shadow-md)]"
                >
                  <div class="flex items-start justify-between gap-2">
                    <span class="text-lg font-semibold text-[var(--text-primary)]">{{ ws.name }}</span>
                    <span [class]="roleBadgeClass(ws.role)"
                    >{{ roleLabel(ws.role) }}</span>
                  </div>

                  <span class="text-sm text-[var(--text-secondary)]">{{ ws.tenantName }}</span>

                  @if (ws.marketplaces.length > 0) {
                    <div class="flex gap-1.5">
                      @for (mp of ws.marketplaces; track mp) {
                        <span class="rounded-[var(--radius-sm)] bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-xs font-medium text-[var(--text-secondary)]">
                          {{ mpShortLabel(mp) }}
                        </span>
                      }
                    </div>
                  }

                  <span class="text-sm text-[var(--text-secondary)]">
                    {{ 'workspace_selector.members_count' | translate:{ count: ws.membersCount } }}
                  </span>
                </button>
              }
            </div>

            <div class="mt-6 flex justify-center">
              <button
                (click)="onCreateWorkspace()"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] bg-transparent px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              >
                {{ 'workspace_selector.create' | translate }}
              </button>
            </div>
          </dp-centered-content>
        }
      }
    </div>
  `,
})
export class WorkspaceSelectorComponent implements OnInit {
  private readonly workspaceApi = inject(WorkspaceApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected readonly state = signal<PageState>('loading');
  protected readonly workspaces = signal<WorkspaceCard[]>([]);
  protected readonly userEmail = signal('');

  ngOnInit(): void {
    this.loadData();
  }

  protected loadData(): void {
    this.state.set('loading');

    this.authService.ensureUser().subscribe({
      next: (profile) => {
        if (!profile) {
          this.state.set('error');
          return;
        }

        this.userEmail.set(profile.email);

        this.workspaceApi.listWorkspaces().subscribe({
          next: (details) => {
            const cards = this.mergeData(profile.memberships, details);
            this.workspaces.set(cards);
            this.state.set(cards.length > 0 ? 'loaded' : 'empty');
          },
          error: () => this.state.set('error'),
        });
      },
      error: () => this.state.set('error'),
    });
  }

  protected onSelectWorkspace(ws: WorkspaceCard): void {
    localStorage.setItem(LAST_WORKSPACE_KEY, String(ws.id));
    this.router.navigate(['/workspace', ws.id, 'grid']);
  }

  protected onCreateWorkspace(): void {
    this.router.navigate(['/onboarding']);
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  protected roleLabel(role: WorkspaceRole): string {
    return this.translate.instant(`role.${role}`);
  }

  protected roleBadgeClass(role: WorkspaceRole): string {
    const base = 'inline-flex shrink-0 rounded-[var(--radius-sm)] px-2 py-0.5 text-xs font-medium';
    switch (role) {
      case 'OWNER':
        return `${base} bg-[var(--accent-primary)] text-white`;
      case 'ADMIN':
      case 'PRICING_MANAGER':
        return `${base} bg-[var(--accent-subtle)] text-[var(--accent-primary)]`;
      case 'OPERATOR':
      case 'ANALYST':
        return `${base} bg-[var(--bg-tertiary)] text-[var(--text-primary)]`;
      case 'VIEWER':
        return `${base} bg-[var(--bg-tertiary)] text-[var(--text-secondary)]`;
    }
  }

  protected mpShortLabel(type: string): string {
    return getMarketplaceShortLabel(type);
  }

  private mergeData(memberships: WorkspaceMembership[], details: WorkspaceDetail[]): WorkspaceCard[] {
    const detailMap = new Map(details.map((d) => [d.id, d]));

    return memberships.map((m) => {
      const detail = detailMap.get(m.workspaceId);
      return {
        id: m.workspaceId,
        name: m.workspaceName,
        tenantName: m.tenantName,
        membersCount: detail?.membersCount ?? 1,
        connectionsCount: detail?.connectionsCount ?? 0,
        role: m.role,
        marketplaces: detail?.marketplaceTypes ?? [],
      };
    });
  }
}
