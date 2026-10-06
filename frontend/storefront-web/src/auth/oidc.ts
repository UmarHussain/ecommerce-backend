import { InMemoryWebStorage, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

export const userManagerSettings: UserManagerSettings = {
  authority: 'http://localhost:8180/realms/ecommerce-local',
  client_id: 'storefront-spa',
  redirect_uri: 'http://localhost:5173/callback',
  post_logout_redirect_uri: 'http://localhost:5173/',
  silent_redirect_uri: 'http://localhost:5173/silent-renew',
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true,
  loadUserInfo: true,
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
}

export const oidcConfig = userManagerSettings
