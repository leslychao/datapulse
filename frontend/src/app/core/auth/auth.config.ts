import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '@env';

export const authConfig: AuthConfig = {
  issuer: environment.keycloak.issuer,
  clientId: environment.keycloak.clientId,
  redirectUri: environment.keycloak.redirectUri,
  postLogoutRedirectUri: environment.keycloak.postLogoutRedirectUri,
  scope: environment.keycloak.scope,
  responseType: 'code',
  requireHttps: false,
  useSilentRefresh: true,
  silentRefreshRedirectUri: `${window.location.origin}/silent-refresh.html`,
  sessionChecksEnabled: false,
  showDebugInformation: !environment.production,
};
