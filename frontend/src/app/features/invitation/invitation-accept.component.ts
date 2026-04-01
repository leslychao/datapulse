import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { InvitationApiService } from '@core/api/invitation-api.service';
import { AuthService } from '@core/auth/auth.service';
import { AcceptInvitationResponse, WorkspaceRole } from '@core/models';
import { MinimalTopBarComponent } from '@shared/layout/minimal-top-bar.component';
import { CenteredContentComponent } from '@shared/layout/centered-content.component';
import { StatusMessageComponent } from '@shared/layout/status-message.component';

type InvitationState = 'loading' | 'success' | 'expired' | 'alreadyAccepted' | 'notFound' | 'invalidLink' | 'serverError';

const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: 'Владелец',
  ADMIN: 'Администратор',
  PRICING_MANAGER: 'Менеджер цен',
  OPERATOR: 'Оператор',
  ANALYST: 'Аналитик',
  VIEWER: 'Наблюдатель',
};

@Component({
  selector: 'dp-invitation-accept',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MinimalTopBarComponent, CenteredContentComponent, StatusMessageComponent],
  template: `
    <div class="flex h-screen flex-col bg-[var(--bg-secondary)]">
      <dp-minimal-top-bar (logoutClick)="onLogout()" />

      <dp-centered-content>
        @switch (state()) {
          @case ('loading') {
            <dp-status-message
              icon="spinner"
              title="Принимаем приглашение..."
            />
          }

          @case ('success') {
            <dp-status-message
              icon="success"
              title="Добро пожаловать!"
              [description]="successDescription()"
              actionLabel="Перейти в пространство"
              (actionClick)="onGoToWorkspace()"
            />
          }

          @case ('expired') {
            <dp-status-message
              icon="warning"
              title="Приглашение истекло"
              description="Срок действия этого приглашения истёк. Попросите администратора отправить новое."
              actionLabel="На главную"
              (actionClick)="onGoHome()"
            />
          }

          @case ('alreadyAccepted') {
            <dp-status-message
              icon="info"
              title="Приглашение уже принято"
              description="Вы уже являетесь участником этого пространства."
              actionLabel="Перейти в пространство"
              (actionClick)="onGoToWorkspace()"
            />
          }

          @case ('notFound') {
            <dp-status-message
              icon="error"
              title="Приглашение не найдено"
              description="Приглашение не существует или было удалено."
              actionLabel="На главную"
              (actionClick)="onGoHome()"
            />
          }

          @case ('invalidLink') {
            <dp-status-message
              icon="error"
              title="Некорректная ссылка"
              description="Ссылка на приглашение повреждена или неполна."
              actionLabel="На главную"
              (actionClick)="onGoHome()"
            />
          }

          @case ('serverError') {
            <dp-status-message
              icon="error"
              title="Ошибка сервера"
              description="Не удалось обработать приглашение. Попробуйте позже."
              actionLabel="Повторить"
              (actionClick)="onRetry()"
            />
          }
        }
      </dp-centered-content>
    </div>
  `,
})
export class InvitationAcceptComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly invitationApi = inject(InvitationApiService);
  private readonly authService = inject(AuthService);

  protected readonly state = signal<InvitationState>('loading');

  private token = '';
  private result: AcceptInvitationResponse | null = null;

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParams['token'] ?? '';
    if (!this.token) {
      this.state.set('invalidLink');
      return;
    }
    this.acceptInvitation();
  }

  protected successDescription(): string {
    if (!this.result) return '';
    const roleLabel = ROLE_LABELS[this.result.role as WorkspaceRole] ?? this.result.role;
    return `Вы присоединились к пространству «${this.result.workspaceName}» с ролью ${roleLabel}.`;
  }

  protected onGoToWorkspace(): void {
    if (this.result) {
      this.router.navigate(['/workspace', this.result.workspaceId, 'grid']);
    }
  }

  protected onGoHome(): void {
    this.router.navigate(['/']);
  }

  protected onRetry(): void {
    this.state.set('loading');
    this.acceptInvitation();
  }

  protected onLogout(): void {
    this.authService.logout();
  }

  private acceptInvitation(): void {
    this.invitationApi.acceptInvitation(this.token).subscribe({
      next: (response) => {
        this.result = response;
        this.state.set('success');
      },
      error: (err: HttpErrorResponse) => {
        switch (err.status) {
          case 409:
            this.result = err.error as AcceptInvitationResponse;
            this.state.set('alreadyAccepted');
            break;
          case 410:
            this.state.set('expired');
            break;
          case 404:
            this.state.set('notFound');
            break;
          default:
            this.state.set('serverError');
        }
      },
    });
  }
}
