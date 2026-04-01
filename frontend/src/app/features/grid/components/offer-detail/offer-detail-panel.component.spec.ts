import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, signal } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OfferDetailPanelComponent } from './offer-detail-panel.component';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { OfferApiService } from '@core/api/offer-api.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { OfferDetail } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildOfferDetail(overrides: Partial<OfferDetail> = {}): OfferDetail {
  return {
    id: 100, skuCode: 'SKU-001', productName: 'Test Product', marketplaceType: 'WB',
    connectionId: 1, connectionName: 'WB Main', status: 'ACTIVE', category: 'Electronics',
    currentPrice: 1000, discountPrice: 900, costPrice: 500, marginPct: 50,
    availableStock: 100, daysOfCover: 45, stockRisk: 'NORMAL',
    revenue30d: 50000, netPnl30d: 15000, velocity14d: 3.5, returnRatePct: 2.1,
    activePolicy: 'Target Margin', lastDecision: 'CHANGE',
    lastActionStatus: 'PENDING_APPROVAL',
    promoStatus: null, manualLock: false, simulatedPrice: null, simulatedDeltaPct: null,
    lastSyncAt: new Date().toISOString(), dataFreshness: 'FRESH',
    categoryId: 10, brand: 'TestBrand', lockedPrice: null, lockReason: null,
    lockExpiresAt: null, policyName: 'Target Margin', policyStrategy: 'TARGET_MARGIN',
    policyMode: 'SEMI_AUTO', lastDecisionDate: new Date().toISOString(),
    lastDecisionExplanation: 'Margin below target',
    lastActionDate: new Date().toISOString(), lastActionMode: 'LIVE',
    promoName: null, promoPrice: null, promoEndDate: null,
    warehouses: [],
    ...overrides,
  };
}

describe('OfferDetailPanelComponent', () => {
  let fixture: ComponentFixture<OfferDetailPanelComponent>;
  let component: OfferDetailPanelComponent;
  let el: HTMLElement;
  let offerApiSpy: jasmine.SpyObj<OfferApiService>;
  let panelService: DetailPanelService;

  function setup(offerOverrides: Partial<OfferDetail> = {}) {
    offerApiSpy = jasmine.createSpyObj('OfferApiService', [
      'getOffer', 'getPriceJournal', 'getPromoJournal', 'getActionHistory',
      'approveAction', 'rejectAction', 'resumeAction',
    ]);
    offerApiSpy.getOffer.and.returnValue(of(buildOfferDetail(offerOverrides)));
    offerApiSpy.getPriceJournal.and.returnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true }));
    offerApiSpy.getPromoJournal.and.returnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true }));
    offerApiSpy.getActionHistory.and.returnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true }));

    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const wsStore = { currentWorkspaceId: () => 1 };

    panelService = new DetailPanelService();
    panelService.open('offer', 100);

    TestBed.configureTestingModule({
      imports: [
        OfferDetailPanelComponent,
        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: FakeLoader } }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAngularQuery(new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })),
        { provide: OfferApiService, useValue: offerApiSpy },
        { provide: DetailPanelService, useValue: panelService },
        { provide: WorkspaceContextStore, useValue: wsStore },
        { provide: ToastService, useValue: toastSpy },
      ],
    });

    fixture = TestBed.createComponent(OfferDetailPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('with loaded offer', () => {
    beforeEach(async () => {
      setup();
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;
    });

    it('should show product name in header', () => {
      const h3 = el.querySelector('h3');
      expect(h3?.textContent?.trim()).toBe('Test Product');
    });

    it('should show marketplace badge', () => {
      const badge = el.querySelector('dp-marketplace-badge');
      expect(badge).toBeTruthy();
    });

    it('should show connection name', () => {
      expect(el.textContent).toContain('WB Main');
    });

    it('should show overview tab by default', () => {
      expect(component.activeTab()).toBe('overview');
      const overviewTab = el.querySelector('dp-offer-overview-tab');
      expect(overviewTab).toBeTruthy();
    });

    it('should switch to price-journal tab on click', () => {
      const tabButtons = el.querySelectorAll('.border-b button');
      const priceJournalTab = Array.from(tabButtons).find(b =>
        b.textContent?.includes('detail.tab.price_journal'),
      );
      priceJournalTab?.dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(component.activeTab()).toBe('price-journal');
      expect(el.querySelector('dp-offer-price-journal-tab')).toBeTruthy();
    });

    it('should switch to promo-journal tab on click', () => {
      const tabButtons = el.querySelectorAll('.border-b button');
      const tab = Array.from(tabButtons).find(b =>
        b.textContent?.includes('detail.tab.promo_journal'),
      );
      tab?.dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(component.activeTab()).toBe('promo-journal');
    });

    it('should switch to action-history tab on click', () => {
      const tabButtons = el.querySelectorAll('.border-b button');
      const tab = Array.from(tabButtons).find(b =>
        b.textContent?.includes('detail.tab.action_history'),
      );
      tab?.dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(component.activeTab()).toBe('action-history');
    });

    it('should switch to stock tab on click', () => {
      const tabButtons = el.querySelectorAll('.border-b button');
      const tab = Array.from(tabButtons).find(b =>
        b.textContent?.includes('detail.tab.stock'),
      );
      tab?.dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(component.activeTab()).toBe('stock');
    });

    it('should close panel on close button click', () => {
      spyOn(panelService, 'close');
      const closeBtn = el.querySelector('[aria-label]') as HTMLButtonElement;
      closeBtn?.click();
      expect(panelService.close).toHaveBeenCalled();
    });

    it('should render all 5 tab buttons', () => {
      const tabButtons = Array.from(el.querySelectorAll('.border-b button')).filter(b =>
        b.textContent?.includes('detail.tab.'),
      );
      expect(tabButtons.length).toBe(5);
    });
  });

  describe('action buttons visibility', () => {
    it('should show approve and reject when PENDING_APPROVAL', async () => {
      setup({ lastActionStatus: 'PENDING_APPROVAL' });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

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

    it('should show hold when APPROVED', async () => {
      setup({ lastActionStatus: 'APPROVED' });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

      const buttons = el.querySelectorAll('button');
      const holdBtn = Array.from(buttons).find(b =>
        b.textContent?.includes('detail.actions.hold'),
      );
      expect(holdBtn).toBeTruthy();
    });

    it('should show resume when ON_HOLD', async () => {
      setup({ lastActionStatus: 'ON_HOLD' });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

      const buttons = el.querySelectorAll('button');
      const resumeBtn = Array.from(buttons).find(b =>
        b.textContent?.includes('detail.actions.resume'),
      );
      expect(resumeBtn).toBeTruthy();
    });

    it('should hide action buttons for SUCCEEDED', async () => {
      setup({ lastActionStatus: 'SUCCEEDED' });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

      const buttons = el.querySelectorAll('button');
      const actionBtns = Array.from(buttons).filter(b => {
        const text = b.textContent ?? '';
        return text.includes('detail.actions.approve')
          || text.includes('detail.actions.reject')
          || text.includes('detail.actions.hold')
          || text.includes('detail.actions.resume');
      });
      expect(actionBtns.length).toBe(0);
    });

    it('should show lock button when not locked', async () => {
      setup({ manualLock: false });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

      const buttons = el.querySelectorAll('button');
      const lockBtn = Array.from(buttons).find(b =>
        b.textContent?.includes('detail.actions.lock'),
      );
      expect(lockBtn).toBeTruthy();
    });

    it('should show unlock button when locked', async () => {
      setup({ manualLock: true });
      await fixture.whenStable();
      fixture.detectChanges();
      el = fixture.nativeElement;

      const buttons = el.querySelectorAll('button');
      const unlockBtn = Array.from(buttons).find(b =>
        b.textContent?.includes('detail.actions.unlock'),
      );
      expect(unlockBtn).toBeTruthy();
    });
  });

  describe('query enabling', () => {
    it('should not enable query when entity type is connection', () => {
      setup();
      panelService.open('connection', 5);
      fixture.detectChanges();

      expect(component.offerQuery.isDisabled).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('should show spinner when query is pending', () => {
      offerApiSpy = jasmine.createSpyObj('OfferApiService', ['getOffer']);
      offerApiSpy.getOffer.and.returnValue(new Promise(() => {})); // never resolves

      panelService = new DetailPanelService();
      panelService.open('offer', 100);

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [
          OfferDetailPanelComponent,
          TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: FakeLoader } }),
        ],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideAngularQuery(new QueryClient({
            defaultOptions: { queries: { retry: false } },
          })),
          { provide: OfferApiService, useValue: offerApiSpy },
          { provide: DetailPanelService, useValue: panelService },
          { provide: WorkspaceContextStore, useValue: { currentWorkspaceId: () => 1 } },
          { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        ],
      });

      fixture = TestBed.createComponent(OfferDetailPanelComponent);
      fixture.detectChanges();
      el = fixture.nativeElement;

      const spinner = el.querySelector('.dp-spinner');
      expect(spinner).toBeTruthy();
    });
  });
});
