import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { of } from 'rxjs';

import { OfferStockTabComponent } from './offer-stock-tab.component';
import { OfferDetail } from '@core/models';

class FakeLoader implements TranslateLoader {
  getTranslation() {
    return of({});
  }
}

function buildOffer(overrides: Partial<OfferDetail> = {}): OfferDetail {
  return {
    offerId: 100, skuCode: 'SKU-001', productName: 'Test', marketplaceType: 'WB',
    connectionId: 1, connectionName: 'WB Main', status: 'ACTIVE', category: null,
    currentPrice: 1000, discountPrice: null, costPrice: 500, marginPct: 50,
    availableStock: 100, daysOfCover: 45, stockRisk: 'NORMAL',
    revenue30d: null, netPnl30d: null, velocity14d: 3.5, returnRatePct: null,
    activePolicy: null, lastDecision: null, lastActionStatus: null,
    promoStatus: null, manualLock: false, simulatedPrice: null, simulatedDeltaPct: null,
    lastSyncAt: null, dataFreshness: null,
    categoryId: null, brand: null, lockedPrice: null, lockReason: null, lockExpiresAt: null,
    policyName: null, policyStrategy: null, policyMode: null,
    lastDecisionDate: null, lastDecisionExplanation: null,
    lastActionDate: null, lastActionMode: null,
    promoName: null, promoPrice: null, promoEndDate: null,
    warehouses: [],
    ...overrides,
  };
}

@Component({
  standalone: true,
  imports: [OfferStockTabComponent],
  template: `<dp-offer-stock-tab [offer]="offerSignal()" />`,
})
class TestHostComponent {
  offerSignal = signal<OfferDetail>(buildOffer());
}

describe('OfferStockTabComponent', () => {
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

  it('should show total available stock', () => {
    expect(el.textContent).toContain('100');
  });

  it('should show dash for null stock', () => {
    host.offerSignal.set(buildOffer({ availableStock: null }));
    fixture.detectChanges();
    expect(el.textContent).toContain('—');
  });

  it('should show days of cover with comma separator', () => {
    host.offerSignal.set(buildOffer({ daysOfCover: 45.3 }));
    fixture.detectChanges();
    expect(el.textContent).toContain('45,3');
  });

  it('should show dash for null days of cover', () => {
    host.offerSignal.set(buildOffer({ daysOfCover: null }));
    fixture.detectChanges();
    expect(el.textContent).toContain('—');
  });

  it('should show risk badge for CRITICAL', () => {
    host.offerSignal.set(buildOffer({ stockRisk: 'CRITICAL' }));
    fixture.detectChanges();
    expect(el.textContent).toContain('grid.stock_risk.CRITICAL');
  });

  it('should render warehouse table when warehouses present', () => {
    host.offerSignal.set(buildOffer({
      warehouses: [
        { warehouseName: 'Moscow', available: 50, reserved: 10, daysOfCover: 30 },
        { warehouseName: 'SPb', available: 50, reserved: 5, daysOfCover: 60 },
      ],
    }));
    fixture.detectChanges();

    const table = el.querySelector('table');
    expect(table).toBeTruthy();
    const rows = table!.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Moscow');
    expect(rows[1].textContent).toContain('SPb');
  });

  it('should hide warehouse table when empty', () => {
    host.offerSignal.set(buildOffer({ warehouses: [] }));
    fixture.detectChanges();
    expect(el.querySelector('table')).toBeNull();
  });

  it('should show velocity with unit', () => {
    host.offerSignal.set(buildOffer({ velocity14d: 3.5 }));
    fixture.detectChanges();
    expect(el.textContent).toContain('3,5');
  });

  it('should show dash for null velocity', () => {
    host.offerSignal.set(buildOffer({ velocity14d: null }));
    fixture.detectChanges();
    expect(el.textContent).toContain('—');
  });

  describe('component methods', () => {
    let component: OfferStockTabComponent;

    beforeEach(() => {
      const debugEl = fixture.debugElement.query(
        (de) => de.componentInstance instanceof OfferStockTabComponent,
      );
      component = debugEl.componentInstance;
    });

    it('riskColor returns error for CRITICAL', () => {
      expect(component['riskColor']('CRITICAL')).toBe('error');
    });

    it('riskColor returns warning for WARNING', () => {
      expect(component['riskColor']('WARNING')).toBe('warning');
    });

    it('riskColor returns success for NORMAL', () => {
      expect(component['riskColor']('NORMAL')).toBe('success');
    });

    it('whRowClass returns red class for low cover', () => {
      expect(component['whRowClass'](3)).toContain('status-error');
    });

    it('whRowClass returns yellow class for medium cover', () => {
      expect(component['whRowClass'](10)).toContain('status-warning');
    });

    it('whRowClass returns empty for normal cover', () => {
      expect(component['whRowClass'](20)).toBe('');
    });

    it('whRowClass returns empty for null cover', () => {
      expect(component['whRowClass'](null)).toBe('');
    });
  });
});
