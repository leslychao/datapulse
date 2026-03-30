import { Component, inject } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

const authConfig: AuthConfig = {
  issuer: environment.keycloak.issuer,
  clientId: environment.keycloak.clientId,
  redirectUri: environment.keycloak.redirectUri,
  postLogoutRedirectUri: environment.keycloak.postLogoutRedirectUri,
  scope: environment.keycloak.scope,
  responseType: 'code',
  showDebugInformation: !environment.production,
};

@Component({
  selector: 'dp-landing',
  standalone: true,
  templateUrl: './landing.component.html',
})
export class LandingComponent {
  private readonly oauthService = inject(OAuthService);

  constructor() {
    this.oauthService.configure(authConfig);
    this.oauthService.loadDiscoveryDocumentAndTryLogin();
  }

  login(): void {
    this.oauthService.initCodeFlow();
  }

  register(): void {
    const registrationUrl =
      `${environment.keycloak.issuer}/protocol/openid-connect/registrations` +
      `?client_id=${environment.keycloak.clientId}` +
      `&redirect_uri=${encodeURIComponent(environment.keycloak.redirectUri)}` +
      `&response_type=code` +
      `&scope=${encodeURIComponent(environment.keycloak.scope)}`;
    window.location.href = registrationUrl;
  }
}
