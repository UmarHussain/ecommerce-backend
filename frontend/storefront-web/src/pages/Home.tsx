import { useAuth } from 'react-oidc-context'
import { Link } from 'react-router-dom'

export function HomePage() {
  const auth = useAuth()
  return (
    <main className="page">
      <p className="eyebrow">E-commerce Local Platform</p>
      <h1>Storefront</h1>
      <p>Public catalog stays anonymous. Sign-in uses Authorization Code + PKCE against Keycloak.</p>
      {auth.isAuthenticated ? (
        <p>Signed in as {auth.user?.profile.email ?? auth.user?.profile.preferred_username}</p>
      ) : (
        <p>You are browsing anonymously.</p>
      )}
      <div className="actions">
        {auth.isAuthenticated ? (
          <button type="button" onClick={() => void auth.signoutRedirect()}>Sign out</button>
        ) : (
          <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
        )}
        <Link to="/profile">Profile</Link>
        <a href="/api/v1/store/catalog/products">Browse catalog API</a>
      </div>
    </main>
  )
}
