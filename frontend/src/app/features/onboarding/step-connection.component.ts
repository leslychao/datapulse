import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  Output,
  signal,
} from '@angular/core';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { WebSocketService } from '@core/websocket/websocket.service';
import {
  MarketplaceType,
  ConnectionStatus,
  MARKETPLACE_REGISTRY,
  MarketplaceConfig,
  getMarketplaceConfig,
  CredentialFieldDef,
} from '@core/models';
import { SpinnerComponent } from '@shared/layout/spinner.component';

type ValidationState = 'idle' | 'submitting' | 'validating' | 'success' | 'failure' | 'timeout';

const POLL_TIMEOUT_MS = 30_000;
const SUCCESS_REDIRECT_DELAY = 2000;

@Component({
  selector: 'dp-step-connection',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SpinnerComponent, TranslatePipe],
  template: `
    <div class="flex flex-col gap-6">
      <div>
        <h2 class="text-lg font-semibold text-[var(--text-primary)]">{{ 'onboarding.connection.title' | translate }}</h2>
        <p class="mt-1 text-sm text-[var(--text-secondary)]">
          {{ 'onboarding.connection.description' | translate }}
        </p>
      </div>

      <div class="flex gap-4">
        @for (mp of marketplaces; track mp.type) {
          <button
            (click)="onSelectMarketplace(mp.type)"
            class="flex h-[140px] w-[200px] cursor-pointer flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border-2 transition-all"
            [class]="selectedMarketplace() === mp.type
              ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[var(--shadow-sm)]'
              : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)] hover:shadow-[var(--shadow-sm)]'"
          >
            <span class="text-2xl font-bold" [style.color]="mp.badgeBg">{{ mp.shortLabel }}</span>
            <span class="text-sm text-[var(--text-secondary)]">{{ mp.label }}</span>
          </button>
        }
      </div>

      @if (selectedMarketplace()) {
        <div class="animate-[slideDown_200ms_ease] flex flex-col gap-4">
          <form [formGroup]="form()" (ngSubmit)="onSubmit()" class="flex flex-col gap-4">
            <div class="flex flex-col gap-1.5">
              <label class="text-sm font-medium text-[var(--text-primary)]">{{ 'onboarding.connection.name_label' | translate }}</label>
              <input
                formControlName="name"
                type="text"
                [placeholder]="'onboarding.connection.name_placeholder' | translate"
                class="rounded-[var(--radius-md)] border bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
                [class]="form().get('name')?.touched && form().get('name')?.invalid
                  ? 'border-[var(--status-error)]'
                  : 'border-[var(--border-default)]'"
              />
            </div>

            @for (field of credentialFields(); track field.key) {
              <div class="flex flex-col gap-1.5">
                <label class="text-sm font-medium text-[var(--text-primary)]">{{ field.labelKey | translate }}</label>
                @if (field.inputType === 'textarea') {
                  <div class="relative">
                    <textarea
                      [formControlName]="field.key"
                      rows="3"
                      [placeholder]="field.placeholderKey ? (field.placeholderKey | translate) : ''"
                      class="w-full resize-none rounded-[var(--radius-md)] border bg-[var(--bg-primary)] px-3 py-2 pr-10 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
                      [class]="form().get(field.key)?.touched && form().get(field.key)?.invalid
                        ? 'border-[var(--status-error)]'
                        : 'border-[var(--border-default)]'"
                      [style.webkitTextSecurity]="showToken() ? 'none' : 'disc'"
                    ></textarea>
                    <button
                      type="button"
                      (click)="showToken.set(!showToken())"
                      class="absolute right-2 top-2 cursor-pointer p-1 text-[var(--text-tertiary)] transition-colors hover:text-[var(--text-secondary)]"
                    >
                      @if (showToken()) {
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                        </svg>
                      } @else {
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                          <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        </svg>
                      }
                    </button>
                  </div>
                } @else {
                  <input
                    [formControlName]="field.key"
                    [type]="field.inputType === 'password' ? (showToken() ? 'text' : 'password') : 'text'"
                    [placeholder]="field.placeholderKey ? (field.placeholderKey | translate) : ''"
                    class="rounded-[var(--radius-md)] border bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
                    [class]="form().get(field.key)?.touched && form().get(field.key)?.invalid
                      ? 'border-[var(--status-error)]'
                      : 'border-[var(--border-default)]'"
                  />
                }
                @if (field.hintKey) {
                  <span class="text-xs text-[var(--text-tertiary)]">{{ field.hintKey | translate }}</span>
                }
              </div>
            }
            @if (hasSecretFields()) {
              <button
                type="button"
                (click)="showToken.set(!showToken())"
                class="self-start text-xs text-[var(--text-tertiary)] transition-colors hover:text-[var(--text-secondary)]"
              >
                {{ showToken() ? ('onboarding.connection.hide' | translate) : ('onboarding.connection.show' | translate) }}
              </button>
            }

            @if (validationState() === 'failure') {
              <div class="rounded-[var(--radius-md)] border border-[var(--status-error)] bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] px-3 py-2 text-sm text-[var(--status-error)]">
                {{ errorMessage() }}
              </div>
            }

            @if (validationState() === 'success') {
              <div class="rounded-[var(--radius-md)] border border-[var(--status-success)] bg-[color-mix(in_srgb,var(--status-success)_8%,transparent)] px-3 py-2 text-sm text-[var(--status-success)]">
                {{ 'onboarding.connection.success' | translate }}
              </div>
            }

            @if (validationState() === 'timeout') {
              <div class="rounded-[var(--radius-md)] border border-[var(--status-warning)] bg-[color-mix(in_srgb,var(--status-warning)_8%,transparent)] px-3 py-2 text-sm text-[var(--status-warning)]">
                {{ 'onboarding.connection.timeout' | translate }}
              </div>
            }

            <button
              type="submit"
              [disabled]="!canSubmit()"
              class="flex cursor-pointer items-center justify-center gap-2 self-end rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (validationState() === 'submitting' || validationState() === 'validating' || validationState() === 'timeout') {
                <dp-spinner [size]="14" color="white" />
                @if (validationState() === 'submitting') {
                  {{ 'onboarding.connection.submitting' | translate }}
                } @else {
                  {{ 'onboarding.connection.validating' | translate }}
                }
              } @else {
                {{ 'onboarding.connection.connect' | translate }}
              }
            </button>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slideDown {
      from { opacity: 0; transform: translateY(-8px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `],
})
export class StepConnectionComponent implements OnDestroy {
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly webSocket = inject(WebSocketService);
  private readonly translate = inject(TranslateService);

  @Input() workspaceId!: number;
  @Output() completed = new EventEmitter<void>();
  @Output() skipped = new EventEmitter<void>();

  protected readonly marketplaces = MARKETPLACE_REGISTRY;
  protected readonly selectedMarketplace = signal<MarketplaceType | null>(null);
  protected readonly validationState = signal<ValidationState>('idle');
  protected readonly errorMessage = signal('');
  protected readonly showToken = signal(false);

  private readonly validatingConnectionId = signal<number | null>(null);
  private timeoutHandle: ReturnType<typeof setTimeout> | null = null;

  protected readonly credentialFields = signal<CredentialFieldDef[]>([]);
  protected readonly hasSecretFields = computed(() =>
    this.credentialFields().some(f => f.inputType === 'password' || f.inputType === 'textarea'),
  );

  protected form = signal<FormGroup>(new FormGroup({
    name: new FormControl('', [Validators.required]),
  }));

  protected canSubmit = signal(true);

  private readonly validationConnectionQuery = injectQuery(() => {
    const state = this.validationState();
    const id = this.validatingConnectionId();
    const watching = (state === 'validating' || state === 'timeout') && id != null && id > 0;
    return {
      queryKey: ['connection', id ?? -1],
      queryFn: () => lastValueFrom(this.connectionApi.getConnection(id!)),
      enabled: watching,
      staleTime: 0,
      refetchInterval: watching ? 3_000 : false,
    };
  });

  constructor() {
    effect(() => {
      const conn = this.validationConnectionQuery.data();
      const state = this.validationState();
      if ((state !== 'validating' && state !== 'timeout') || !conn) {
        return;
      }
      this.handlePollResult(conn.status, conn.lastErrorCode);
    });
  }

  ngOnDestroy(): void {
    this.stopValidationWatch();
  }

  protected onSelectMarketplace(type: MarketplaceType): void {
    this.selectedMarketplace.set(type);
    this.validationState.set('idle');
    this.errorMessage.set('');
    this.stopValidationWatch();

    const config = getMarketplaceConfig(type);
    this.credentialFields.set(config.credentialFields);

    const controls: Record<string, FormControl> = {
      name: new FormControl('', [Validators.required]),
    };
    for (const field of config.credentialFields) {
      controls[field.key] = new FormControl('', [Validators.required]);
    }
    this.form.set(new FormGroup(controls));
  }

  protected onSubmit(): void {
    const marketplace = this.selectedMarketplace();
    if (!marketplace) return;

    const currentForm = this.form();
    if (currentForm.invalid) {
      currentForm.markAllAsTouched();
      return;
    }

    this.validationState.set('submitting');
    this.updateCanSubmit();

    const name = currentForm.get('name')!.value!;
    const config = getMarketplaceConfig(marketplace);
    const credentials: Record<string, string> = {};
    for (const field of config.credentialFields) {
      credentials[field.key] = currentForm.get(field.key)!.value!;
    }

    this.connectionApi
      .createConnection({
        marketplaceType: marketplace,
        name,
        credentials,
      } as any)
      .subscribe({
        next: (connection) => {
          this.startValidationWatch(connection.id);
        },
        error: () => {
          this.validationState.set('failure');
          this.errorMessage.set(this.translate.instant('onboarding.connection.create_error'));
          this.updateCanSubmit();
        },
      });
  }

  private startValidationWatch(connectionId: number): void {
    this.validatingConnectionId.set(connectionId);
    this.validationState.set('validating');
    this.updateCanSubmit();

    this.webSocket.connect(this.workspaceId);
    this.webSocket.subscribeToWorkspace(this.workspaceId);

    this.timeoutHandle = setTimeout(() => {
      if (this.validationState() === 'validating') {
        this.validationState.set('timeout');
      }
    }, POLL_TIMEOUT_MS);
  }

  private handlePollResult(status: ConnectionStatus, errorCode: string | null): void {
    const state = this.validationState();
    if (state !== 'validating' && state !== 'timeout') {
      return;
    }

    if (status === 'ACTIVE') {
      this.stopValidationWatch();
      this.validationState.set('success');
      this.updateCanSubmit();
      setTimeout(() => this.completed.emit(), SUCCESS_REDIRECT_DELAY);
      return;
    }

    if (status === 'AUTH_FAILED') {
      this.stopValidationWatch();
      this.validationState.set('failure');
      this.errorMessage.set(
        errorCode
          ? this.translate.instant(`onboarding.connection.error.${errorCode}`)
          : this.translate.instant('onboarding.connection.validation_error'),
      );
      this.updateCanSubmit();
    }
  }

  private stopValidationWatch(): void {
    if (this.timeoutHandle !== null) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }
    this.validatingConnectionId.set(null);
    this.webSocket.disconnect();
  }

  private updateCanSubmit(): void {
    const state = this.validationState();
    this.canSubmit.set(state === 'idle' || state === 'failure');
  }
}
