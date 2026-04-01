import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'dpDateFormat',
  standalone: true,
  pure: true,
})
export class DateFormatPipe implements PipeTransform {
  transform(iso: string | null, style: 'short' | 'full' = 'full'): string {
    if (!iso) return '—';
    const options: Intl.DateTimeFormatOptions = style === 'short'
      ? { day: '2-digit', month: '2-digit', year: 'numeric' }
      : { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' };
    return new Date(iso).toLocaleString('ru-RU', options);
  }
}
