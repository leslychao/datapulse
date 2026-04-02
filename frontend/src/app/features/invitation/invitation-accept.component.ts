import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { InvitationApiService } from '@core/api/invitation-api.service';
import { AuthService } from '@core/auth/auth.service';
import { AcceptInvitationResponse, WorkspaceRole } from '@core/models';
import { MinimalTopBarComponent } from '@shared/layout/minimal-top-bar.component';
import { CenteredContentComponent } from '@shared/layout/centered-content.component';
import { StatusMessageComponent } from '@shared/layout/status-message.component';

type InvitationState = 'loading' | 'success' | 'expired' | 'alreadyAccepted' | 'notFound' | 'invalidLink' | 'serverError' | 'networkError';

@Component({
  selector: 'dp-invitation-accept',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MinimalTopBarComponent, CenteredContentComponent, StatusMessageComponent, TranslatePipe],
  template: `
    <div class="flex h-screen flex-col bg-[var(--bg-secondary)]">
      <dp-minimal-top-bar (logoutClick)="onLogout()" />

      <dp-centered-content>
        @switch (state()) {
          @case ('loading') {
            <dp-status-message
              icon="spinner"
              [title]="'invitation.loading' | translate"
            />
          }

          @case ('success') {
            <dp-status-message
              icon="success"
              [title]="'invitation.success_title' | translate"
              [description]="successDescription()"
              [actionLabel]="'invitation.go_to_workspace' | translate"
              (actionClick)="onGoToWorkspace()"
            />
          }

          @case ('expired') {
            <dp-status-message
              icon="warning"
              [title]="'invitation.expired_title' | translate"
              [description]="'invitation.expired_description' | translate"
              [actionLabel]="'actions.go_home' | translate"
              (actionClick)="onGoHome()"
            />
          }

          @case ('alreadyAccepted') {
            <dp-status-message
              icon="info"
              [title]="'invitation.already_accepted_title' | translate"
              [description]="'invitation.already_accepted_description' | translate"
              [actionLabel]="'invitation.go_to_workspace' | translate"
              (actionClick)="onGoToWorkspace()"
            />
          }

          @case ('notFound') {
            <dp-status-message
              icon="error"
              [title]="'invitation.not_found_title' | translate"
              [description]="'invitation.not_found_description' | translate"
              [actionLabel]="'actions.go_home' | translate"
              (actionClick)="onGoHome()"
            />
          }

          @case ('invalidLink') {
            <dp-status-message
              icon="error"
              [title]="'invitation.invalid_link_title' | translate"
              [description]="'invitation.invalid_link_description' | translate"
              [actionLabel]="'actions.go_home' | translate"
              (actionClick)="onGoHome()"
            />
          }

          @case ('serverError') {
            <dp-status-message
              icon="error"
              [title]="'invitation.server_error_title' | translate"
              [description]="'invitation.server_error_description' | translate"
              [actionLabel]="'actions.retry' | translate"
              (actionClick)="onRetry()"
            />
          }

          @case ('networkError') {
            <dp-status-message
              icon="error"
              [title]="'invitation.network_error_title' | translate"
              [description]="'invitation.network_error_description' | translate"
              [actionLabel]="'actions.retry' | translate"
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
  private readonly translate = inject(TranslateService);

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
    const roleLabel = this.translate.instant(`role.${this.result.role}`);
    return this.translate.instant('invitation.success_description', {
      workspace: this.result.workspaceName,
      role: roleLabel,
    });
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
          case 0:
            this.state.set('networkError');
            break;
          case 404:
            this.state.set('notFound');
            break;
          case 409:
            this.result = err.error as AcceptInvitationResponse;
            this.state.set('alreadyAccepted');
            break;
          case 410:
            this.state.set('expired');
            break;
          default:
            this.state.set('serverError');
        }
      },
    });
  }
}
