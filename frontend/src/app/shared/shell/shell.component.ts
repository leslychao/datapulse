import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

@Component({
  selector: 'dp-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  private readonly oauthService = inject(OAuthService);

  get userName(): string {
    const claims = this.oauthService.getIdentityClaims() as Record<string, string> | null;
    return claims?.['preferred_username'] ?? 'User';
  }

  logout(): void {
    this.oauthService.logOut();
  }
}
