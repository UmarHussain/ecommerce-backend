import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { ApiError, api } from '../api/client'
import { canManageRoles } from '../auth/permissions'

interface RoleDefinition {
  name: string
  kind: string
  description: string | null
  permissions: string[]
  privileged: boolean
}

export function RolesPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [roles, setRoles] = useState<RoleDefinition[]>([])
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [bundlePermissions, setBundlePermissions] = useState('catalog.read')

  useEffect(() => {
    if (!auth.user?.access_token) {
      return
    }
    api<RoleDefinition[]>('/api/v1/admin/roles', auth.user.access_token)
      .then(setRoles)
      .catch((cause: unknown) => setError(cause instanceof ApiError ? cause.message : 'Could not load roles'))
  }, [auth.user?.access_token])

  async function create(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) {
      return
    }
    try {
      const created = await api<RoleDefinition>('/api/v1/admin/roles', auth.user.access_token, {
        method: 'POST',
        body: JSON.stringify({
          name,
          description: 'Custom application bundle',
          permissions: bundlePermissions.split(',').map((item) => item.trim()).filter(Boolean),
        }),
      })
      setRoles((current) => [...current, created])
      setName('')
      setError(null)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Bundle creation was rejected')
    }
  }

  return (
    <main className="page">
      <h1>Roles</h1>
      {error ? <p className="error">{error}</p> : null}
      <ul className="list">
        {roles.map((role) => (
          <li key={role.name}>
            <strong>{role.name}</strong> ({role.kind}{role.privileged ? ', privileged' : ''})
            <div>{role.permissions.join(', ')}</div>
          </li>
        ))}
      </ul>
      {canManageRoles(permissions) ? (
        <form className="stack" onSubmit={create}>
          <h2>Create custom bundle</h2>
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="WarehouseLead" required />
          <input value={bundlePermissions} onChange={(event) => setBundlePermissions(event.target.value)} placeholder="catalog.read,inventory.read" />
          <button type="submit">Create bundle</button>
        </form>
      ) : (
        <p>Only PLATFORM_ADMIN can create custom role bundles.</p>
      )}
    </main>
  )
}
