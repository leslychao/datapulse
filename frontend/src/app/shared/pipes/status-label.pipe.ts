import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

type StatusColor = 'success' | 'error' | 'warning' | 'info' | 'neutral';

const COLORS: Record<string, StatusColor> = {
  ACTIVE: 'success',
  PENDING_VALIDATION: 'info',
  AUTH_FAILED: 'error',
  ERROR: 'error',
  DISABLED: 'neutral',
  ARCHIVED: 'neutral',
  OPEN: 'error',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
  AUTO_RESOLVED: 'success',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'info',
  EXECUTING: 'info',
  CONFIRMED: 'success',
  FAILED: 'error',
  EXPIRED: 'neutral',
  CANCELLED: 'neutral',
  RECONCILIATION_PENDING: 'warning',
  DRAFT: 'neutral',
  PAUSED: 'warning',
};

@Pipe({
  name: 'dpStatusLabel',
  standalone: true,
  pure: true,
})
export class StatusLabelPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(status: string): string {
    return this.translate.instant(`status.${status.toLowerCase()}`);
  }
}

@Pipe({
  name: 'dpStatusColor',
  standalone: true,
  pure: true,
})
export class StatusColorPipe implements PipeTransform {
  transform(status: string): StatusColor {
    return COLORS[status] ?? 'neutral';
  }
}
