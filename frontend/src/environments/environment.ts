export const environment = {
  production: false,
  apiUrl: '/api',
  keycloak: {
    issuer: 'http://localhost:8080/realms/datapulse',
    clientId: 'datapulse-spa',
    redirectUri: 'http://localhost:4200/callback',
    postLogoutRedirectUri: 'http://localhost:4200/',
    scope: 'openid email profile',
  },
  wsUrl: 'ws://localhost:8081/ws',
};
