import { useAuth } from 'react-oidc-context'
import { Link } from 'react-router-dom'

export function HomePage({ permissions, denied }: { permissions: string[]; denied: boolean }) {
  const auth = useAuth()
  return (
    <main className="page">
      <p className="eyebrow">E-commerce Local Platform</p>
      <h1>Administration</h1>
      {denied ? (
        <p className="error">This account can sign in to the admin client but has no staff privileges.</p>
      ) : (
        <p>Staff administration uses the same Keycloak identity as the storefront, with a separate OAuth client.</p>
      )}
      {auth.isAuthenticated ? (
        <p>Signed in as {auth.user?.profile.email ?? auth.user?.profile.preferred_username}</p>
      ) : (
        <p>Sign in to continue. Tokens stay in memory.</p>
      )}
      <div className="actions">
        {auth.isAuthenticated ? (
          <button type="button" onClick={() => void auth.signoutRedirect()}>Sign out</button>
        ) : (
          <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
        )}
        {permissions.includes('PERM_user.read') ? <Link to="/users">Users</Link> : null}
        {permissions.includes('PERM_role.read') ? <Link to="/roles">Roles</Link> : null}
        {permissions.includes('PERM_catalog.read') ? <Link to="/catalog/products">Catalog</Link> : null}
        {permissions.includes('PERM_inventory.read') ? <Link to="/inventory">Inventory</Link> : null}
      </div>
    </main>
  )
}
