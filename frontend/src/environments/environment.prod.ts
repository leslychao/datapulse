export const environment = {
  production: true,
  apiUrl: '/api',
  keycloak: {
    issuer: '/realms/datapulse',
    clientId: 'datapulse-spa',
    redirectUri: '/callback',
    postLogoutRedirectUri: '/',
    scope: 'openid email profile',
  },
  wsUrl: '/ws',
};
