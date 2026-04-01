import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OfferActionHistoryTabComponent } from './offer-action-history-tab.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
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
    first: number === 0,
    last: number === totalPages - 1,
  };
}

@Component({
  standalone: true,
  imports: [OfferActionHistoryTabComponent],
  template: `<dp-offer-action-history-tab [offerId]="offerId()" />`,
})
class TestHostComponent {
  offerId = signal(100);
}

describe('OfferActionHistoryTabComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let el: HTMLElement;
  let offerApiSpy: jasmine.SpyObj<OfferApiService>;
  let toastSpy: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    offerApiSpy = jasmine.createSpyObj('OfferApiService', [
      'getActionHistory', 'approveAction', 'rejectAction', 'resumeAction',
    ]);
    toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    offerApiSpy.getActionHistory.and.returnValue(
      of(buildPage([
        buildEntry({ id: 1, status: 'PENDING_APPROVAL', targetPrice: 1100 }),
        buildEntry({ id: 2, status: 'SUCCEEDED', targetPrice: 900, actualPrice: 900 }),
        buildEntry({ id: 3, status: 'ON_HOLD', reason: 'Waiting for approval' }),
      ])),
    );

    const wsStore = { currentWorkspaceId: signal(1) };

    await TestBed.configureTestingModule({
      imports: [
        TestHostComponent,
        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: FakeLoader } }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAngularQuery(new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })),
        { provide: OfferApiService, useValue: offerApiSpy },
        { provide: WorkspaceContextStore, useValue: wsStore },
        { provide: ToastService, useValue: toastSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement;
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
    expect(el.textContent).toContain('1100');
  });

  it('should show actual price when present', () => {
    expect(el.textContent).toContain('900');
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
    if (succeededCard) {
      const buttons = succeededCard.querySelectorAll('button');
      const approveBtn = Array.from(buttons).find(b =>
        b.textContent?.includes('detail.actions.approve'),
      );
      expect(approveBtn).toBeFalsy();
    }
  });

  it('should call approveAction on approve click', async () => {
    offerApiSpy.approveAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await fixture.whenStable();
    expect(offerApiSpy.approveAction).toHaveBeenCalledWith(1);
  });

  it('should show success toast after approve', async () => {
    offerApiSpy.approveAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await fixture.whenStable();
    fixture.detectChanges();

    expect(toastSpy.success).toHaveBeenCalled();
  });

  it('should show error toast on approve failure', async () => {
    offerApiSpy.approveAction.and.returnValue(throwError(() => new Error('fail')));

    const buttons = el.querySelectorAll('button');
    const approveBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.approve'),
    );
    approveBtn?.click();

    await fixture.whenStable();
    fixture.detectChanges();

    expect(toastSpy.error).toHaveBeenCalled();
  });

  it('should call rejectAction on reject click', async () => {
    offerApiSpy.rejectAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const rejectBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.reject'),
    );
    rejectBtn?.click();

    await fixture.whenStable();
    expect(offerApiSpy.rejectAction).toHaveBeenCalledWith(1, '');
  });

  it('should call resumeAction on resume click', async () => {
    offerApiSpy.resumeAction.and.returnValue(of(void 0));

    const buttons = el.querySelectorAll('button');
    const resumeBtn = Array.from(buttons).find(b =>
      b.textContent?.includes('detail.actions.resume'),
    );
    resumeBtn?.click();

    await fixture.whenStable();
    expect(offerApiSpy.resumeAction).toHaveBeenCalledWith(3);
  });

  describe('component methods', () => {
    let component: OfferActionHistoryTabComponent;

    beforeEach(() => {
      const debugEl = fixture.debugElement.query(
        (de) => de.componentInstance instanceof OfferActionHistoryTabComponent,
      );
      component = debugEl.componentInstance;
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
      offerApiSpy.getActionHistory.and.returnValue(of(buildPage([])));

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;
    });

    it('should show empty state message', () => {
      expect(el.textContent).toContain('detail.actions.empty');
    });
  });
});
