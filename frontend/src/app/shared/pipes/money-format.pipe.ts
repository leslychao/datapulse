import { Pipe, PipeTransform } from '@angular/core';

import { formatMoney } from '@shared/utils/format.utils';

@Pipe({
  name: 'dpMoney',
  standalone: true,
  pure: true,
})
export class MoneyFormatPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 2, currency = '₽'): string {
    return formatMoney(value, decimals, currency);
  }
}
