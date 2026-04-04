import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OfferActionHistoryTabComponent } from './offer-action-history-tab.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ActionApiService } from '@core/api/action-api.service';
import { OfferApiService } from '@core/api/offer-api.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ActionHistoryEntry, Page } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildEntry(overrides: Partial<ActionHistoryEntry> = {}): ActionHistoryEntry {
  return {
    id: 1,
    actionDate: new Date().toISOString(),
    actionType: 'SET_PRICE',
    status: 'SUCCEEDED',
    targetPrice: 1100,
    actualPrice: 1100,
    executionMode: 'LIVE',
    reason: null,
    initiatedBy: null,
    ...overrides,
  };
}

function buildPage(
  entries: ActionHistoryEntry[] = [],
  number = 0,
  totalPages = 1,
): Page<ActionHistoryEntry> {
  return {
    content: entries,
    totalElements: entries.length,
    totalPages,
    number,
    size: 20,
  };
}

/** Collapse spaces/NBSP so formatted money like "1 100,00" still matches substring "1100". */
function compactForDigitMatch(text: string | null): string {
  return (text ?? '').replace(/\u00a0/g, '').replace(/\s/g, '');
}

describe('OfferActionHistoryTabComponent', () => {
  let fixture: ComponentFixture<OfferActionHistoryTabComponent>;
  let el: HTMLElement;
  let offerApiSpy: jasmine.SpyObj<OfferApiService>;
  let actionApiSpy: jasmine.SpyObj<ActionApiService>;
  let toastSpy: jasmine.SpyObj<ToastService>;

  async function settleHistoryQuery(fixtureRef: ComponentFixture<OfferActionHistoryTabComponent>): Promise<void> {
    for (let i = 0; i < 30; i++) {
      fixtureRef.detectChanges();
      TestBed.flushEffects();
      if (!fixtureRef.componentInstance.historyQuery.isPending()) {
        return;
      }
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }
  }

  async function settleAfterUiWork(
    fixtureRef: ComponentFixture<OfferActionHistoryTabComponent>,
    done: () => boolean,
  ): Promise<void> {
    for (let i = 0; i < 50; i++) {
      fixtureRef.detectChanges();
      TestBed.flushEffects();
      if (done()) {
        return;
      }
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }
  }

  async function mountTab(historyPage: Page<ActionHistoryEntry>): Promise<void> {
    TestBed.resetTestingModule();
    actionApiSpy = jasmine.createSpyObj('ActionApiService', [
      'approveAction', 'rejectAction', 'resumeAction',
    ]);
    offerApiSpy = jasmine.createSpyObj('OfferApiService', ['getActionHistory']);
    toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    offerApiSpy.getActionHistory.and.returnValue(of(historyPage));

    const wsStore = { currentWorkspaceId: signal(1) };

    await TestBed.configureTestingModule({
      imports: [
        OfferActionHistoryTabComponent,
        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: FakeLoader } }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAngularQuery(new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })),
        { provide: OfferApiService, useValue: offerApiSpy },
        { provide: ActionApiService, useValue: actionApiSpy },
        { provide: WorkspaceContextStore, useValue: wsStore },
        { provide: ToastService, useValue: toastSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OfferActionHistoryTabComponent);
    fixture.componentRef.setInput('offerId', 100);
    await settleHistoryQuery(fixture);
    fixture.detectChanges();
    el = fixture.nativeElement;
  }

  beforeEach(async () => {
    await mountTab(
      buildPage([
        buildEntry({ id: 1, status: 'PENDING_APPROVAL', targetPrice: 1100 }),
        buildEntry({ id: 2, status: 'SUCCEEDED', targetPrice: 900, actualPrice: 900 }),
        buildEntry({ id: 3, status: 'ON_HOLD', reason: 'Waiting for approval' }),
      ]),
    );
  });

  it('should render action entries', () => {
    const cards = el.querySelectorAll('.border.border-\\[var\\(--border-default\\)\\]');
    expect(cards.length).toBeGreaterThanOrEqual(3);
  });

  it('should show status badge for each entry', () => {
    expect(el.textContent).toContain('grid.action_status.PENDING_APPROVAL');
    expect(el.textContent).toContain('grid.action_status.SUCCEEDED');
    expect(el.textContent).toContain('grid.action_status.ON_HOLD');
  });

  it('should show target price when present', () => {
    expect(compactForDigitMatch(el.textContent)).toContain('1100');
  });

  it('should show actual price when present', () => {
    expect(compactForDigitMatch(el.textContent)).toContain('900');
  });

  it('should show execution mode', () => {
    expect(el.textContent).toContain('LIVE');
  });

  it('should show reason when present', () => {
    expect(el.textContent).toContain('Waiting for approval');
  });

  it('should show approve and reject buttons for PENDING_APPROVAL', () => {
    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    const rejectBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.reject'),
    );
    expect(approveBtn).toBeTruthy();
    expect(rejectBtn).toBeTruthy();
  });

  it('should show resume button for ON_HOLD', () => {
    const buttons = el.querySelectorAll('button');
    const resumeBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.resume'),
    );
    expect(resumeBtn).toBeTruthy();
  });

  it('should not show approve/reject for SUCCEEDED entries', () => {
    const succeededCard = el.querySelectorAll('.border.border-\\[var\\(--border-default\\)\\]')[1];
    expect(succeededCard).toBeTruthy();
    const buttons = succeededCard!.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    expect(approveBtn).toBeFalsy();
  });

  it('should call approveAction on approve click', async () => {
    actionApiSpy.approveAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await settleAfterUiWork(fixture, () => actionApiSpy.approveAction.calls.count() > 0);
    expect(actionApiSpy.approveAction).toHaveBeenCalledWith(1, 1);
  });

  it('should show success toast after approve', async () => {
    actionApiSpy.approveAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await settleAfterUiWork(fixture, () => toastSpy.success.calls.count() > 0);
    expect(toastSpy.success).toHaveBeenCalled();
  });

  it('should show error toast on approve failure', async () => {
    actionApiSpy.approveAction.and.returnValue(throwError(() => new Error('fail')));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await settleAfterUiWork(fixture, () => toastSpy.error.calls.count() > 0);
    expect(toastSpy.error).toHaveBeenCalled();
  });

  it('should call rejectAction on reject click', async () => {
    actionApiSpy.rejectAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const rejectBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.reject'),
    );
    rejectBtn?.click();

    await settleAfterUiWork(fixture, () => actionApiSpy.rejectAction.calls.count() > 0);
    expect(actionApiSpy.rejectAction).toHaveBeenCalledWith(1, 1, '');
  });

  it('should call resumeAction on resume click', async () => {
    actionApiSpy.resumeAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const resumeBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.resume'),
    );
    resumeBtn?.click();

    await settleAfterUiWork(fixture, () => actionApiSpy.resumeAction.calls.count() > 0);
    expect(actionApiSpy.resumeAction).toHaveBeenCalledWith(1, 3);
  });

  describe('component methods', () => {
    let component: OfferActionHistoryTabComponent;

    beforeEach(() => {
      component = fixture.componentInstance;
    });

    it('statusColor returns success for SUCCEEDED', () => {
      expect(component['statusColor']('SUCCEEDED')).toBe('success');
    });

    it('statusColor returns error for FAILED', () => {
      expect(component['statusColor']('FAILED')).toBe('error');
    });

    it('statusColor returns warning for ON_HOLD', () => {
      expect(component['statusColor']('ON_HOLD')).toBe('warning');
    });

    it('statusColor returns warning for RETRY_SCHEDULED', () => {
      expect(component['statusColor']('RETRY_SCHEDULED')).toBe('warning');
    });

    it('statusColor returns info for PENDING_APPROVAL', () => {
      expect(component['statusColor']('PENDING_APPROVAL')).toBe('info');
    });

    it('statusColor returns info for EXECUTING', () => {
      expect(component['statusColor']('EXECUTING')).toBe('info');
    });

    it('statusColor returns info for RECONCILIATION_PENDING', () => {
      expect(component['statusColor']('RECONCILIATION_PENDING')).toBe('info');
    });

    it('statusColor returns neutral for CANCELLED', () => {
      expect(component['statusColor']('CANCELLED')).toBe('neutral');
    });

    it('statusColor returns neutral for SUPERSEDED', () => {
      expect(component['statusColor']('SUPERSEDED')).toBe('neutral');
    });

    it('loadMore increments page', () => {
      expect(component.page()).toBe(0);
      component.loadMore();
      expect(component.page()).toBe(1);
    });
  });

  describe('empty state', () => {
    beforeEach(async () => {
      await mountTab(buildPage([]));
    });

    it('should show empty state message', () => {
      expect(el.textContent).toContain('detail.actions.empty');
    });
  });
});
