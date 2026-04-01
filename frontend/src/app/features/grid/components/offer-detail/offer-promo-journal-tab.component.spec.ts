import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OfferPromoJournalTabComponent } from './offer-promo-journal-tab.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { OfferApiService } from '@core/api/offer-api.service';
import { PromoJournalEntry, Page } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildEntry(overrides: Partial<PromoJournalEntry> = {}): PromoJournalEntry {
  return {
    id: 1,
    promoName: 'Spring Sale',
    promoType: 'SALE',
    periodStart: '2025-03-01',
    periodEnd: '2025-03-31',
    participationDecision: 'PARTICIPATE',
    evaluationResult: 'PROFITABLE',
    requiredPrice: 850,
    marginAtPromoPrice: 25,
    marginDeltaPct: -5,
    actionStatus: 'SUCCEEDED',
    explanationSummary: 'Margin acceptable',
    ...overrides,
  };
}

function buildPage(
  entries: PromoJournalEntry[] = [],
  number = 0,
  totalPages = 1,
): Page<PromoJournalEntry> {
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
  imports: [OfferPromoJournalTabComponent],
  template: `<dp-offer-promo-journal-tab [offerId]="offerId()" />`,
})
class TestHostComponent {
  offerId = signal(100);
}

describe('OfferPromoJournalTabComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let el: HTMLElement;
  let offerApiSpy: jasmine.SpyObj<OfferApiService>;

  beforeEach(async () => {
    offerApiSpy = jasmine.createSpyObj('OfferApiService', ['getPromoJournal']);
    offerApiSpy.getPromoJournal.and.returnValue(
      of(buildPage([
        buildEntry(),
        buildEntry({ id: 2, promoName: 'Flash Sale', participationDecision: 'DECLINE' }),
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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement;
  });

  it('should render promo entries', () => {
    expect(el.textContent).toContain('Spring Sale');
    expect(el.textContent).toContain('Flash Sale');
  });

  it('should show participation decision badge', () => {
    expect(el.textContent).toContain('detail.promo.decision_PARTICIPATE');
  });

  it('should show promo name as title', () => {
    const titles = el.querySelectorAll('h5');
    expect(titles.length).toBeGreaterThanOrEqual(2);
    expect(titles[0].textContent).toContain('Spring Sale');
  });

  it('should show explanation summary when present', () => {
    expect(el.textContent).toContain('Margin acceptable');
  });

  describe('component methods', () => {
    let component: OfferPromoJournalTabComponent;

    beforeEach(() => {
      const debugEl = fixture.debugElement.query(
        (de) => de.componentInstance instanceof OfferPromoJournalTabComponent,
      );
      component = debugEl.componentInstance;
    });

    it('participationColor returns success for PARTICIPATE', () => {
      expect(component['participationColor']('PARTICIPATE')).toBe('success');
    });

    it('participationColor returns error for DECLINE', () => {
      expect(component['participationColor']('DECLINE')).toBe('error');
    });

    it('participationColor returns warning for PENDING_REVIEW', () => {
      expect(component['participationColor']('PENDING_REVIEW')).toBe('warning');
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

    it('actionColor returns neutral for unknown status', () => {
      expect(component['actionColor']('CANCELLED')).toBe('neutral');
    });

    it('loadMore increments page', () => {
      expect(component.page()).toBe(0);
      component.loadMore();
      expect(component.page()).toBe(1);
    });
  });
});
