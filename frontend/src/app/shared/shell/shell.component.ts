import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnDestroy, OnInit, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet, ActivatedRoute } from '@angular/router';

import { TopBarComponent } from './top-bar/top-bar.component';
import { ActivityBarComponent } from './activity-bar/activity-bar.component';
import { TabBarComponent } from './tab-bar/tab-bar.component';
import { DetailPanelComponent } from './detail-panel/detail-panel.component';
import { BottomPanelComponent } from './bottom-panel/bottom-panel.component';
import { StatusBarComponent } from './status-bar/status-bar.component';
import { CommandPaletteComponent } from './command-palette/command-palette.component';
import { ToastContainerComponent } from './toast/toast-container.component';
import { OfferDetailPanelComponent } from '@features/grid/components/offer-detail/offer-detail-panel.component';
import { AlertDetailPanelComponent } from '@features/alerts/alert-detail-panel.component';
import { PolicyDetailPanelComponent } from '@features/pricing/policies/policy-detail-panel.component';
import { DecisionDetailPanelComponent } from '@features/pricing/decisions/decision-detail-panel.component';
import { ActionDetailPanelComponent } from '@features/execution/action-detail-panel.component';
import { AutomationBlockerBannerComponent } from './automation-blocker-banner.component';
import { ConnectionLostBannerComponent } from './connection-lost-banner.component';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ShortcutService } from '@shared/services/shortcut.service';
import { WebSocketService } from '@core/websocket/websocket.service';

@Component({
  selector: 'dp-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    TopBarComponent,
    ActivityBarComponent,
    TabBarComponent,
    DetailPanelComponent,
    BottomPanelComponent,
    StatusBarComponent,
    CommandPaletteComponent,
    ToastContainerComponent,
    OfferDetailPanelComponent,
    AlertDetailPanelComponent,
    PolicyDetailPanelComponent,
    DecisionDetailPanelComponent,
    ActionDetailPanelComponent,
    AutomationBlockerBannerComponent,
    ConnectionLostBannerComponent,
  ],
  template: `
    <div class="grid h-screen w-screen overflow-hidden bg-[var(--bg-primary)]"
         [style.grid-template-columns]="'48px 1fr ' + (detailPanel.isOpen() ? detailPanel.width() + 'px' : '0px')"
         style="grid-template-rows: 40px auto 1fr auto 24px;
                grid-template-areas:
                  'topbar  topbar  topbar'
                  'actbar  tabbar  detail'
                  'actbar  main    detail'
                  'actbar  bottom  detail'
                  'status  status  status';">
      <dp-top-bar style="grid-area: topbar"
                  (searchRequested)="openCommandPalette()" />

      <dp-activity-bar style="grid-area: actbar"
                       class="border-r border-[var(--border-default)]" />

      <dp-tab-bar style="grid-area: tabbar" />

      @if (detailPanel.isOpen()) {
        <dp-detail-panel style="grid-area: detail"
                         class="border-l border-[var(--border-default)]">
          @if (detailPanel.entityType() === 'offer') {
            <dp-offer-detail-panel />
          }
          @if (detailPanel.entityType() === 'alert') {
            <dp-alert-detail-panel />
          }
          @if (detailPanel.entityType() === 'policy') {
            <dp-policy-detail-panel />
          }
          @if (detailPanel.entityType() === 'pricing-decision') {
            <dp-decision-detail-panel />
          }
          @if (detailPanel.entityType() === 'action') {
            <dp-action-detail-panel />
          }
        </dp-detail-panel>
      }

      <main style="grid-area: main" class="flex flex-col overflow-hidden bg-[var(--bg-primary)]">
        <dp-automation-blocker-banner />
        <dp-connection-lost-banner />
        <div class="flex flex-1 flex-col overflow-auto min-h-0">
          <router-outlet />
        </div>
      </main>

      <dp-bottom-panel style="grid-area: bottom" />

      <dp-status-bar style="grid-area: status"
                     class="border-t border-[var(--border-default)]" />
    </div>

    <dp-command-palette />
    <dp-toast-container />
  `,
})
export class ShellComponent implements OnInit, OnDestroy {
  protected readonly commandPalette = viewChild.required(CommandPaletteComponent);
  protected readonly detailPanel = inject(DetailPanelService);
  private readonly workspaceStore = inject(WorkspaceContextStore);
  private readonly route = inject(ActivatedRoute);
  private readonly shortcuts = inject(ShortcutService);
  private readonly webSocket = inject(WebSocketService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.shortcuts.init();
  }

  protected openCommandPalette(): void {
    this.commandPalette().open();
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const wsId = Number(params['workspaceId']);
      if (!isNaN(wsId) && wsId > 0) {
        this.workspaceStore.setWorkspace(wsId, '');
        this.webSocket.connect(wsId);
        this.webSocket.subscribeToWorkspace(wsId);
      }
    });
  }

  ngOnDestroy(): void {
    this.webSocket.disconnect();
  }
}
