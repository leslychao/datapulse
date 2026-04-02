import { APP_INITIALIZER, ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TranslateLoader, TranslateService, provideTranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { StaticTranslateLoader } from './core/i18n/static-translate.loader';

function translateAppInitializer(translate: TranslateService) {
  return () => firstValueFrom(translate.use('ru'));
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations(),
    provideAngularQuery(
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1 },
        },
      }),
    ),
    provideTranslateService({
      defaultLanguage: 'ru',
      loader: {
        provide: TranslateLoader,
        useClass: StaticTranslateLoader,
      },
    }),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: translateAppInitializer,
      deps: [TranslateService],
    },
  ],
};
