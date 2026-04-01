import { TestBed } from '@angular/core/testing';
import { DetailPanelService } from './detail-panel.service';

describe('DetailPanelService', () => {
  let service: DetailPanelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DetailPanelService);
  });

  it('should be closed initially', () => {
    expect(service.isOpen()).toBe(false);
    expect(service.entityType()).toBeNull();
    expect(service.entityId()).toBeNull();
  });

  it('should open with entity type and id', () => {
    service.open('offer', 42);

    expect(service.isOpen()).toBe(true);
    expect(service.entityType()).toBe('offer');
    expect(service.entityId()).toBe(42);
  });

  it('should close and reset all signals', () => {
    service.open('offer', 42);
    service.close();

    expect(service.isOpen()).toBe(false);
    expect(service.entityType()).toBeNull();
    expect(service.entityId()).toBeNull();
  });

  it('should toggle from closed to open', () => {
    service.toggle();
    expect(service.isOpen()).toBe(true);
  });

  it('should toggle from open to closed', () => {
    service.open('offer', 1);
    service.toggle();

    expect(service.isOpen()).toBe(false);
  });

  it('should clamp width to minimum', () => {
    service.resize(100);
    expect(service.width()).toBe(320);
  });

  it('should clamp width to max viewport ratio', () => {
    const maxAllowed = Math.floor(window.innerWidth * 0.5);
    service.resize(99999);
    expect(service.width()).toBeLessThanOrEqual(maxAllowed);
  });

  it('should accept width within valid range', () => {
    service.resize(500);
    expect(service.width()).toBe(500);
  });

  it('should update entity when opened with different id', () => {
    service.open('offer', 1);
    expect(service.entityId()).toBe(1);

    service.open('offer', 2);
    expect(service.entityId()).toBe(2);
    expect(service.isOpen()).toBe(true);
  });

  it('should support connection entity type', () => {
    service.open('connection', 5);

    expect(service.entityType()).toBe('connection');
    expect(service.entityId()).toBe(5);
  });
});
