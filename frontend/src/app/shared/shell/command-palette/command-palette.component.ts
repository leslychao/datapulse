import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';

import { SearchApiService } from '@core/api/search-api.service';
import { SearchResult } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

interface PaletteItem {
  type: 'product' | 'policy' | 'promo' | 'view' | 'command';
  label: string;
  sublabel?: string;
  action: () => void;
}

interface PaletteGroup {
  label: string;
  items: PaletteItem[];
}

interface DisplayGroup {
  label: string;
  items: (PaletteItem & { flatIdx: number })[];
}

const ICON_PATHS: Record<string, string> = {
  product:
    '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" x2="12" y1="22.08" y2="12"/>',
  policy:
    '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>',
  promo:
    '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5" fill="currentColor"/>',
  view:
    '<rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/>',
  command:
    '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
};

const STATIC_COMMANDS: { labelKey: string; path: string }[] = [
  { labelKey: 'shell.command_palette.go_grid', path: 'grid' },
  { labelKey: 'shell.command_palette.go_analytics', path: 'analytics' },
  { labelKey: 'shell.command_palette.go_pricing', path: 'pricing' },
  { labelKey: 'shell.command_palette.go_price_actions', path: 'pricing/price-actions' },
  { labelKey: 'shell.command_palette.go_promo', path: 'promo' },
  { labelKey: 'shell.command_palette.go_settings', path: 'settings' },
  { labelKey: 'shell.command_palette.start_sync', path: 'settings/sync' },
  { labelKey: 'shell.command_palette.create_policy', path: 'pricing/new' },
];

@Component({
  selector: 'dp-command-palette',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKeyDown($event)' },
  template: `
    @if (isOpen()) {
      <div class="backdrop" (click)="close()">
        <div class="palette" (click)="$event.stopPropagation()">
          <div class="palette-header">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                 fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
            </svg>
            <input
              #searchInput
              type="text"
              class="palette-input"
              [value]="query()"
              (input)="onQueryChange($event)"
              placeholder="Поиск товаров, политик, команд..."
            />
            <kbd class="palette-kbd">Esc</kbd>
          </div>

          <div class="palette-body">
            @if (loading()) {
              @for (w of shimmerWidths; track w) {
                <div class="palette-shimmer" [style.width]="w"></div>
              }
            } @else if (query().trim().length >= 2 && flatItems().length === 0) {
              <div class="palette-empty">
                Ничего не найдено по запросу «{{ query().trim() }}»
              </div>
            } @else {
              @for (group of displayGroups(); track group.label) {
                <div class="palette-group-header">{{ group.label }}</div>
                @for (item of group.items; track item.flatIdx) {
                  <button
                    class="palette-item"
                    [class.palette-item-active]="item.flatIdx === selectedIndex()"
                    (click)="executeItem(item)"
                    (mouseenter)="selectedIndex.set(item.flatIdx)"
                  >
                    <span class="palette-item-icon" [innerHTML]="iconHtml(item.type)"></span>
                    <span class="palette-item-label">{{ item.label }}</span>
                    @if (item.sublabel) {
                      <span class="palette-item-sublabel">{{ item.sublabel }}</span>
                    }
                  </button>
                }
              }
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 50;
      display: flex;
      justify-content: center;
      padding-top: 20vh;
      background: rgba(0, 0, 0, 0.15);
    }

    .palette {
      width: 560px;
      align-self: flex-start;
      background: var(--bg-primary, #fff);
      border: 1px solid var(--border-default, #e5e7eb);
      border-radius: var(--radius-lg, 8px);
      box-shadow: var(--shadow-md, 0 4px 12px rgba(0, 0, 0, 0.08));
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .palette-header {
      display: flex;
      align-items: center;
      height: 44px;
      padding: 0 12px;
      border-bottom: 1px solid var(--border-default, #e5e7eb);
      gap: 8px;
    }

    .palette-header svg {
      flex-shrink: 0;
      color: var(--text-tertiary, #9ca3af);
    }

    .palette-input {
      flex: 1;
      height: 100%;
      border: none;
      outline: none;
      font-size: 14px;
      color: var(--text-primary, #111827);
      background: transparent;
    }

    .palette-input::placeholder {
      color: var(--text-tertiary, #9ca3af);
    }

    .palette-kbd {
      flex-shrink: 0;
      padding: 2px 6px;
      font-size: 11px;
      font-family: ui-monospace, monospace;
      color: var(--text-tertiary, #9ca3af);
      background: var(--bg-tertiary, #f3f4f6);
      border: 1px solid var(--border-default, #e5e7eb);
      border-radius: 4px;
      line-height: 1.4;
    }

    .palette-body {
      overflow-y: auto;
      max-height: 420px;
      padding: 4px 0;
    }

    .palette-group-header {
      padding: 8px 16px 4px;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: var(--text-tertiary, #9ca3af);
    }

    .palette-item {
      display: flex;
      align-items: center;
      width: 100%;
      height: 36px;
      padding: 0 16px;
      gap: 8px;
      border: none;
      background: transparent;
      cursor: pointer;
      font-size: 13px;
      color: var(--text-primary, #111827);
      text-align: left;
      font-family: inherit;
    }

    .palette-item:hover {
      background: var(--bg-tertiary, #f3f4f6);
    }

    .palette-item-active {
      background: #eff6ff !important;
    }

    .palette-item-icon {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      color: var(--text-secondary, #6b7280);
    }

    .palette-item-label {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .palette-item-sublabel {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--text-tertiary, #9ca3af);
    }

    .palette-empty {
      padding: 24px 16px;
      text-align: center;
      font-size: 13px;
      color: var(--text-secondary, #6b7280);
    }

    .palette-shimmer {
      height: 36px;
      margin: 2px 16px;
      border-radius: 4px;
      background: linear-gradient(
        90deg,
        var(--bg-tertiary, #f3f4f6) 25%,
        #e8e8e8 50%,
        var(--bg-tertiary, #f3f4f6) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `],
})
export class CommandPaletteComponent {
  private readonly router = inject(Router);
  private readonly searchApi = inject(SearchApiService);
  private readonly workspaceStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly el = inject(ElementRef);

  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  private readonly search$ = new Subject<string>();
  private readonly iconCache = new Map<string, SafeHtml>();

  readonly isOpen = signal(false);
  readonly query = signal('');
  readonly loading = signal(false);
  readonly results = signal<SearchResult | null>(null);
  readonly selectedIndex = signal(0);
  readonly shimmerWidths = ['100%', '72%', '88%', '64%'];

  private readonly groups = computed<PaletteGroup[]>(() => {
    const q = this.query().toLowerCase().trim();
    const res = this.results();
    const groups: PaletteGroup[] = [];

    if (q.length >= 2 && res) {
      if (res.products.length > 0) {
        groups.push({
          label: 'ТОВАРЫ',
          items: res.products.slice(0, 5).map((p) => ({
            type: 'product' as const,
            label: p.productName,
            sublabel: `${p.skuCode} · ${p.marketplaceType}`,
            action: () => this.navigateInWorkspace('grid', { product: p.offerId }),
          })),
        });
      }
      if (res.policies.length > 0) {
        groups.push({
          label: 'ПОЛИТИКИ',
          items: res.policies.slice(0, 3).map((p) => ({
            type: 'policy' as const,
            label: p.name,
            action: () => this.navigateInWorkspace(`pricing/policies/${p.policyId}`),
          })),
        });
      }
      if (res.promos.length > 0) {
        groups.push({
          label: 'ПРОМО-АКЦИИ',
          items: res.promos.slice(0, 3).map((p) => ({
            type: 'promo' as const,
            label: p.name,
            action: () => this.navigateInWorkspace(`promo/campaigns/${p.campaignId}`),
          })),
        });
      }
      if (res.views.length > 0) {
        groups.push({
          label: 'ПРЕДСТАВЛЕНИЯ',
          items: res.views.slice(0, 3).map((v) => ({
            type: 'view' as const,
            label: v.name,
            action: () => this.navigateInWorkspace(`grid/views/${v.viewId}`),
          })),
        });
      }
    }

    const matchingCommands = STATIC_COMMANDS.filter(
      (c) =>
        q.length < 2
        || this.translate.instant(c.labelKey).toLowerCase().includes(q),
    );
    if (matchingCommands.length > 0) {
      groups.push({
        label: 'КОМАНДЫ',
        items: matchingCommands.slice(0, 5).map((c) => ({
          type: 'command' as const,
          label: this.translate.instant(c.labelKey),
          action: () => this.navigateInWorkspace(c.path),
        })),
      });
    }

    return groups;
  });

  readonly displayGroups = computed<DisplayGroup[]>(() => {
    let idx = 0;
    return this.groups().map((g) => ({
      label: g.label,
      items: g.items.map((item) => ({ ...item, flatIdx: idx++ })),
    }));
  });

  readonly flatItems = computed(() => this.groups().flatMap((g) => g.items));

  constructor() {
    this.search$
      .pipe(
        switchMap((q) =>
          q.length < 2 ? of(null) : of(q).pipe(debounceTime(300)),
        ),
        switchMap((q) => {
          if (!q) return of(null);
          const wsId = this.workspaceStore.currentWorkspaceId();
          if (!wsId) return of(null);
          this.loading.set(true);
          return this.searchApi.search(wsId, q).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.results.set(result);
        this.loading.set(false);
      });

    effect(() => {
      if (this.isOpen()) {
        setTimeout(() => this.searchInput()?.nativeElement.focus());
      }
    });
  }

  open(): void {
    this.query.set('');
    this.results.set(null);
    this.loading.set(false);
    this.selectedIndex.set(0);
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
  }

  toggle(): void {
    this.isOpen() ? this.close() : this.open();
  }

  onKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      this.toggle();
      return;
    }

    if (!this.isOpen()) return;

    switch (event.key) {
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      case 'ArrowDown':
        event.preventDefault();
        this.moveSelection(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveSelection(-1);
        break;
      case 'Enter':
        event.preventDefault();
        this.executeSelected();
        break;
    }
  }

  onQueryChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.selectedIndex.set(0);

    const trimmed = value.trim();
    if (trimmed.length < 2) {
      this.results.set(null);
      this.loading.set(false);
    }
    this.search$.next(trimmed);
  }

  executeItem(item: PaletteItem): void {
    this.close();
    item.action();
  }

  iconHtml(type: string): SafeHtml {
    if (!this.iconCache.has(type)) {
      const paths = ICON_PATHS[type] ?? '';
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
      this.iconCache.set(type, this.sanitizer.bypassSecurityTrustHtml(svg));
    }
    return this.iconCache.get(type)!;
  }

  private moveSelection(delta: number): void {
    const items = this.flatItems();
    if (items.length === 0) return;
    const current = this.selectedIndex();
    const next = (current + delta + items.length) % items.length;
    this.selectedIndex.set(next);
    requestAnimationFrame(() => {
      const active = this.el.nativeElement.querySelector('.palette-item-active');
      active?.scrollIntoView({ block: 'nearest' });
    });
  }

  private executeSelected(): void {
    const items = this.flatItems();
    const idx = this.selectedIndex();
    if (idx >= 0 && idx < items.length) {
      this.executeItem(items[idx]);
    }
  }

  private navigateInWorkspace(path: string, queryParams?: Record<string, unknown>): void {
    const wsId = this.workspaceStore.currentWorkspaceId();
    if (!wsId) return;
    const segments = ['/workspace', wsId, ...path.split('/')];
    this.router.navigate(segments, queryParams ? { queryParams } : undefined);
  }
}
