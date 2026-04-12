import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { of } from 'rxjs';

import { OfferOverviewTabComponent } from './offer-overview-tab.component';
import { OfferDetail } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildOffer(overrides: Partial<OfferDetail> = {}): OfferDetail {
  return {
    offerId: 100,
    skuCode: 'SKU-001',
    productName: 'Test Product',
    marketplaceType: 'WB',
    connectionName: 'WB Main',
    status: 'ACTIVE',
    category: 'Electronics',
    currentPrice: 1000,
    discountPrice: 900,
    costPrice: 500,
    marginPct: 50,
    availableStock: 100,
    daysOfCover: 45,
    stockRisk: 'NORMAL',
    revenue30d: 50000,
    netPnl30d: 15000,
    velocity14d: 3.5,
    returnRatePct: 2.1,
    activePolicy: 'Target Margin',
    lastDecision: 'CHANGE',
    lastActionStatus: 'SUCCEEDED',
    promoStatus: null,
    manualLock: false,
    simulatedPrice: null,
    simulatedDeltaPct: null,
    lastSyncAt: new Date().toISOString(),
    dataFreshness: 'FRESH',
    categoryId: 10,
    brand: 'TestBrand',
    lockedPrice: null,
    lockReason: null,
    lockExpiresAt: null,
    policyName: 'Target Margin',
    policyStrategy: 'TARGET_MARGIN',
    policyMode: 'SEMI_AUTO',
    lastDecisionDate: new Date().toISOString(),
    lastDecisionExplanation: 'Margin below target',
    lastActionDate: new Date().toISOString(),
    lastActionMode: 'LIVE',
    warehouses: [],
    ...overrides,
  };
}

@Component({
  standalone: true,
  imports: [OfferOverviewTabComponent],
  template: `<dp-offer-overview-tab [offer]="offerSignal()" />`,
})
class TestHostComponent {
  offerSignal = signal<OfferDetail>(buildOffer());
}

describe('OfferOverviewTabComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        TestHostComponent,
        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: FakeLoader } }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
    el = fixture.nativeElement;
  });

  it('should render sku code', () => {
    expect(el.textContent).toContain('SKU-001');
  });

  it('should render product connection name', () => {
    expect(el.textContent).toContain('WB Main');
  });

  it('should show lock emoji when locked', () => {
    host.offerSignal.set(buildOffer({ manualLock: true, lockedPrice: 999 }));
    fixture.detectChanges();
    expect(el.textContent).toContain('🔒');
  });

  it('should show unlock emoji when not locked', () => {
    host.offerSignal.set(buildOffer({ manualLock: false }));
    fixture.detectChanges();
    expect(el.textContent).toContain('🔓');
  });

  it('should show policy section when policy present', () => {
    host.offerSignal.set(buildOffer({ policyName: 'Target Margin' }));
    fixture.detectChanges();
    expect(el.textContent).toContain('Target Margin');
  });

  it('should hide policy section when absent', () => {
    host.offerSignal.set(buildOffer({ policyName: null, policyStrategy: null, policyMode: null }));
    fixture.detectChanges();
    const sections = el.querySelectorAll('section');
    const policySection = Array.from(sections).find(s =>
      s.querySelector('h4')?.textContent?.includes('detail.overview.policy'),
    );
    expect(policySection).toBeUndefined();
  });

  it('should show last decision section when present', () => {
    host.offerSignal.set(buildOffer({ lastDecision: 'CHANGE' }));
    fixture.detectChanges();
    const text = el.textContent ?? '';
    expect(text).toContain('grid.decision.CHANGE');
  });

  it('should hide last decision section when absent', () => {
    host.offerSignal.set(buildOffer({ lastDecision: null }));
    fixture.detectChanges();
    const sections = el.querySelectorAll('section');
    const decisionSection = Array.from(sections).find(s =>
      s.querySelector('h4')?.textContent?.includes('detail.overview.last_decision'),
    );
    expect(decisionSection).toBeUndefined();
  });

  it('should show promo section when participating', () => {
    host.offerSignal.set(buildOffer({
      promoStatus: {
        participating: true,
        campaignName: 'Spring Sale',
        promoPrice: 850,
        endsAt: null,
      },
    }));
    fixture.detectChanges();
    expect(el.textContent).toContain('Spring Sale');
  });

  it('should hide promo section when no promo', () => {
    host.offerSignal.set(buildOffer({ promoStatus: null }));
    fixture.detectChanges();
    const sections = el.querySelectorAll('section');
    const promoSection = Array.from(sections).find(s =>
      s.querySelector('h4')?.textContent?.includes('detail.overview.promo'),
    );
    expect(promoSection).toBeUndefined();
  });

  it('should show dash for null velocity', () => {
    host.offerSignal.set(buildOffer({ velocity14d: null }));
    fixture.detectChanges();
    expect(el.textContent).toContain('—');
  });

  it('should show stock risk badge when present', () => {
    host.offerSignal.set(buildOffer({ stockRisk: 'CRITICAL' }));
    fixture.detectChanges();
    expect(el.textContent).toContain('grid.stock_risk.CRITICAL');
  });

  it('should show last action section when status present', () => {
    host.offerSignal.set(buildOffer({ lastActionStatus: 'FAILED' }));
    fixture.detectChanges();
    expect(el.textContent).toContain('grid.action_status.FAILED');
  });

  it('should hide last action section when no status', () => {
    host.offerSignal.set(buildOffer({ lastActionStatus: null }));
    fixture.detectChanges();
    const sections = el.querySelectorAll('section');
    const actionSection = Array.from(sections).find(s =>
      s.querySelector('h4')?.textContent?.includes('detail.overview.last_action'),
    );
    expect(actionSection).toBeUndefined();
  });

  describe('status color methods', () => {
    let component: OfferOverviewTabComponent;

    beforeEach(() => {
      const debugEl = fixture.debugElement.query(
        (de) => de.componentInstance instanceof OfferOverviewTabComponent,
      );
      component = debugEl.componentInstance;
    });

    it('offerStatusColor returns success for ACTIVE', () => {
      host.offerSignal.set(buildOffer({ status: 'ACTIVE' }));
      fixture.detectChanges();
      expect(component['offerStatusColor']()).toBe('success');
    });

    it('offerStatusColor returns error for BLOCKED', () => {
      host.offerSignal.set(buildOffer({ status: 'BLOCKED' }));
      fixture.detectChanges();
      expect(component['offerStatusColor']()).toBe('error');
    });

    it('offerStatusColor returns neutral for ARCHIVED', () => {
      host.offerSignal.set(buildOffer({ status: 'ARCHIVED' }));
      fixture.detectChanges();
      expect(component['offerStatusColor']()).toBe('neutral');
    });

    it('decisionColor returns info for CHANGE', () => {
      host.offerSignal.set(buildOffer({ lastDecision: 'CHANGE' }));
      fixture.detectChanges();
      expect(component['decisionColor']()).toBe('info');
    });

    it('decisionColor returns warning for HOLD', () => {
      host.offerSignal.set(buildOffer({ lastDecision: 'HOLD' }));
      fixture.detectChanges();
      expect(component['decisionColor']()).toBe('warning');
    });

    it('actionStatusColor returns success for SUCCEEDED', () => {
      host.offerSignal.set(buildOffer({ lastActionStatus: 'SUCCEEDED' }));
      fixture.detectChanges();
      expect(component['actionStatusColor']()).toBe('success');
    });

    it('stockRiskColor returns error for CRITICAL', () => {
      host.offerSignal.set(buildOffer({ stockRisk: 'CRITICAL' }));
      fixture.detectChanges();
      expect(component['stockRiskColor']()).toBe('error');
    });

    it('stockRiskColor returns warning for WARNING', () => {
      host.offerSignal.set(buildOffer({ stockRisk: 'WARNING' }));
      fixture.detectChanges();
      expect(component['stockRiskColor']()).toBe('warning');
    });
  });
});
