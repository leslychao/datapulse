import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OfferPriceJournalTabComponent } from './offer-price-journal-tab.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { OfferApiService } from '@core/api/offer-api.service';
import { PriceJournalEntry, Page } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildEntry(overrides: Partial<PriceJournalEntry> = {}): PriceJournalEntry {
  return {
    id: 1,
    decisionDate: new Date().toISOString(),
    decisionType: 'CHANGE',
    currentPrice: 1000,
    targetPrice: 1100,
    actualPrice: 1100,
    policyName: 'Target Margin',
    policyVersion: 3,
    actionStatus: 'SUCCEEDED',
    executionMode: 'LIVE',
    explanationSummary: 'Margin below target',
    reconciliationSource: null,
    ...overrides,
  };
}

function buildPage(
  entries: PriceJournalEntry[] = [],
  number = 0,
  totalPages = 1,
): Page<PriceJournalEntry> {
  return {
    content: entries,
    totalElements: entries.length,
    totalPages,
    number,
    size: 20,
  };
}

describe('OfferPriceJournalTabComponent', () => {
  let fixture: ComponentFixture<OfferPriceJournalTabComponent>;
  let el: HTMLElement;
  let offerApiSpy: jasmine.SpyObj<OfferApiService>;

  async function settleJournalQuery(fixtureRef: ComponentFixture<OfferPriceJournalTabComponent>): Promise<void> {
    for (let i = 0; i < 30; i++) {
      fixtureRef.detectChanges();
      TestBed.flushEffects();
      if (!fixtureRef.componentInstance.journalQuery.isPending()) {
        return;
      }
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }
  }

  beforeEach(async () => {
    TestBed.resetTestingModule();
    offerApiSpy = jasmine.createSpyObj('OfferApiService', ['getPriceJournal']);
    offerApiSpy.getPriceJournal.and.returnValue(
      of(buildPage([buildEntry(), buildEntry({ id: 2, decisionType: 'SKIP' })])),
    );

    const wsStore = {
      currentWorkspaceId: signal(1),
    };

    await TestBed.configureTestingModule({
      imports: [
        OfferPriceJournalTabComponent,
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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OfferPriceJournalTabComponent);
    fixture.componentRef.setInput('offerId', 100);
    await settleJournalQuery(fixture);
    fixture.detectChanges();
    el = fixture.nativeElement;
  });

  it('should render journal entries', () => {
    const cards = el.querySelectorAll('.rounded-\\[var\\(--radius-md\\)\\]');
    expect(cards.length).toBeGreaterThanOrEqual(2);
  });

  it('should show decision type badge', () => {
    expect(el.textContent).toContain('grid.decision.CHANGE');
  });

  it('should show policy name and version', () => {
    expect(el.textContent).toContain('Target Margin');
    expect(el.textContent).toContain('v3');
  });

  it('should show explanation summary', () => {
    expect(el.textContent).toContain('Margin below target');
  });

  describe('component methods', () => {
    let component: OfferPriceJournalTabComponent;

    beforeEach(() => {
      component = fixture.componentInstance;
    });

    it('decisionColor returns info for CHANGE', () => {
      expect(component['decisionColor']('CHANGE')).toBe('info');
    });

    it('decisionColor returns warning for HOLD', () => {
      expect(component['decisionColor']('HOLD')).toBe('warning');
    });

    it('decisionColor returns neutral for SKIP', () => {
      expect(component['decisionColor']('SKIP')).toBe('neutral');
    });

    it('actionColor returns success for SUCCEEDED', () => {
      expect(component['actionColor']('SUCCEEDED')).toBe('success');
    });

    it('actionColor returns error for FAILED', () => {
      expect(component['actionColor']('FAILED')).toBe('error');
    });

    it('actionColor returns warning for ON_HOLD', () => {
      expect(component['actionColor']('ON_HOLD')).toBe('warning');
    });

    it('actionColor returns info for PENDING_APPROVAL', () => {
      expect(component['actionColor']('PENDING_APPROVAL')).toBe('info');
    });

    it('actionColor returns neutral for CANCELLED', () => {
      expect(component['actionColor']('CANCELLED')).toBe('neutral');
    });

    it('loadMore increments page', () => {
      expect(component.page()).toBe(0);
      component.loadMore();
      expect(component.page()).toBe(1);
    });

    it('onDecisionFilterChange resets page to 0', () => {
      component.loadMore();
      expect(component.page()).toBe(1);

      const event = { target: { value: 'SKIP' } } as unknown as Event;
      component.onDecisionFilterChange(event);

      expect(component.decisionFilter()).toBe('SKIP');
      expect(component.page()).toBe(0);
    });
  });
});
