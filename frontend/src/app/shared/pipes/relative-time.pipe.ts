import { Pipe, PipeTransform } from '@angular/core';

import { formatRelativeTime } from '@shared/utils/format.utils';

@Pipe({
  name: 'dpRelativeTime',
  standalone: true,
  pure: true,
})
export class RelativeTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatRelativeTime(iso);
  }
}
