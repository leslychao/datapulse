import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

/** Backend ErrorResponse shape (message / messageKey + args). */
export interface ApiErrorBody {
  message?: string;
  messageKey?: string;
  args?: Record<string, string | number | boolean>;
}

/**
 * Resolves a user-visible message from an HTTP error: prefers {@link ApiErrorBody.messageKey}
 * when present in ru.json, otherwise falls back to {@param fallbackKey}.
 */
export function translateApiErrorMessage(
  translate: TranslateService,
  error: unknown,
  fallbackKey: string,
): string {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    const body = error.error as ApiErrorBody;
    const key =
      body.messageKey
      ?? (typeof body.message === 'string' && /^[a-z][a-z0-9_.]+$/.test(body.message)
        ? body.message
        : undefined);
    if (key) {
      const args = body.args ?? {};
      const out = translate.instant(key, args);
      if (out !== key) {
        return out;
      }
    }
  }
  return translate.instant(fallbackKey);
}
