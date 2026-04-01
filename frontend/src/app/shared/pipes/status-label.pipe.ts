import { Pipe, PipeTransform } from '@angular/core';

import { ConnectionStatus } from '@core/models';

type StatusColor = 'success' | 'error' | 'warning' | 'info' | 'neutral';

const LABELS: Record<string, string> = {
  ACTIVE: 'Активно',
  PENDING_VALIDATION: 'Проверка',
  AUTH_FAILED: 'Ошибка авторизации',
  ERROR: 'Ошибка',
  DISABLED: 'Отключено',
  ARCHIVED: 'В архиве',
};

const COLORS: Record<string, StatusColor> = {
  ACTIVE: 'success',
  PENDING_VALIDATION: 'info',
  AUTH_FAILED: 'error',
  ERROR: 'error',
  DISABLED: 'neutral',
  ARCHIVED: 'neutral',
};

@Pipe({
  name: 'dpStatusLabel',
  standalone: true,
  pure: true,
})
export class StatusLabelPipe implements PipeTransform {
  transform(status: ConnectionStatus | string): string {
    return LABELS[status] ?? status;
  }
}

@Pipe({
  name: 'dpStatusColor',
  standalone: true,
  pure: true,
})
export class StatusColorPipe implements PipeTransform {
  transform(status: ConnectionStatus | string): StatusColor {
    return COLORS[status] ?? 'neutral';
  }
}
