import { Injectable } from '@angular/core';
import { TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';

import ru from '../../../locale/ru.json';

/**
 * Bundles {@link ru.json} into the app bundle so translations do not depend on
 * fetching {@code /locale/ru.json} (breaks under wrong base URL, caching, or SPA routing).
 */
@Injectable()
export class StaticTranslateLoader implements TranslateLoader {
  getTranslation(lang: string): Observable<typeof ru> {
    void lang;
    return of(ru);
  }
}
