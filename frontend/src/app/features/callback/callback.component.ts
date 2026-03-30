import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

@Component({
  selector: 'dp-callback',
  standalone: true,
  template: `
    <div class="flex h-screen items-center justify-center">
      <p class="text-[var(--text-secondary)]">Авторизация...</p>
    </div>
  `,
})
export class CallbackComponent implements OnInit {
  private readonly oauthService = inject(OAuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.router.navigate(['/app']);
      } else {
        this.router.navigate(['/']);
      }
    });
  }
}
