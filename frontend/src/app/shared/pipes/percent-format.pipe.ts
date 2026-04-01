import { Pipe, PipeTransform } from '@angular/core';

import { formatPercent } from '@shared/utils/format.utils';

@Pipe({
  name: 'dpPercent',
  standalone: true,
  pure: true,
})
export class PercentFormatPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 1, showSign = false): string {
    return formatPercent(value, decimals, showSign);
  }
}
