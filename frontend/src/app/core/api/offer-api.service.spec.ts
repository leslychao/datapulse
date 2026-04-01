import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { OfferApiService } from './offer-api.service';
import { environment } from '@env';

describe('OfferApiService', () => {
  let service: OfferApiService;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OfferApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getOffer should call correct URL', () => {
    service.getOffer(1, 100).subscribe();

    const req = httpMock.expectOne(`${base}/workspace/1/offers/100`);
    expect(req.request.method).toBe('GET');
    req.flush({ offerId: 100 });
  });

  it('getPriceJournal should pass pagination params', () => {
    service.getPriceJournal(1, 100, 0, 20).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${base}/workspace/1/offers/100/price-journal`,
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], totalElements: 0 });
  });

  it('getPriceJournal should pass decisionType filter', () => {
    service.getPriceJournal(1, 100, 0, 20, 'SKIP').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${base}/workspace/1/offers/100/price-journal`,
    );
    expect(req.request.params.get('decisionType')).toBe('SKIP');
    req.flush({ content: [], totalElements: 0 });
  });

  it('getPromoJournal should call correct URL with params', () => {
    service.getPromoJournal(1, 100, 2, 10).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${base}/workspace/1/offers/100/promo-journal`,
    );
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], totalElements: 0 });
  });

  it('getActionHistory should call correct URL', () => {
    service.getActionHistory(1, 100, 0, 20).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${base}/workspace/1/offers/100/action-history`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0 });
  });

  it('approveAction should POST to correct URL', () => {
    service.approveAction(42).subscribe();

    const req = httpMock.expectOne(`${base}/actions/42/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('rejectAction should POST with cancelReason body', () => {
    service.rejectAction(42, 'Too expensive').subscribe();

    const req = httpMock.expectOne(`${base}/actions/42/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cancelReason: 'Too expensive' });
    req.flush(null);
  });

  it('resumeAction should POST to correct URL', () => {
    service.resumeAction(42).subscribe();

    const req = httpMock.expectOne(`${base}/actions/42/resume`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('lockOffer should POST with lock request body', () => {
    const lockReq = { lockedPrice: 999, reason: 'Hold for promo' };
    service.lockOffer(1, 100, lockReq).subscribe();

    const req = httpMock.expectOne(`${base}/workspace/1/offers/100/lock`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(lockReq);
    req.flush(null);
  });

  it('unlockOffer should POST empty body', () => {
    service.unlockOffer(1, 100).subscribe();

    const req = httpMock.expectOne(`${base}/workspace/1/offers/100/unlock`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });
});
