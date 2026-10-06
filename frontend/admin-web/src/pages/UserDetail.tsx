import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { ApiError, api, newIdempotencyKey } from '../api/client'
import { canAssignRoles } from '../auth/permissions'

interface UserDetail {
  user: { id: string; email: string; displayName: string; enabled: boolean }
  directRoles: string[]
  effectiveRoles: string[]
  staffProfile: { department: string | null; onboardingStatus: string } | null
}

export function UserDetailPage({ permissions }: { permissions: string[] }) {
  const { userId } = useParams()
  const auth = useAuth()
  const [detail, setDetail] = useState<UserDetail | null>(null)
  const [role, setRole] = useState('CATALOG_VIEWER')
  const [error, setError] = useState<string | null>(null)

  async function load() {
    if (!auth.user?.access_token || !userId) {
      return
    }
    try {
      setDetail(await api<UserDetail>(`/api/v1/admin/users/${userId}`, auth.user.access_token))
      setError(null)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Could not load user')
    }
  }

  useEffect(() => {
    void load()
  }, [auth.user?.access_token, userId])

  async function assign(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token || !userId) {
      return
    }
    try {
      await api(`/api/v1/admin/users/${userId}/roles`, auth.user.access_token, {
        method: 'POST',
        headers: { 'Idempotency-Key': newIdempotencyKey() },
        body: JSON.stringify({ roles: [role] }),
      })
      await load()
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Role assignment was rejected')
    }
  }

  async function remove(name: string) {
    if (!auth.user?.access_token || !userId) {
      return
    }
    try {
      await api(`/api/v1/admin/users/${userId}/roles/${encodeURIComponent(name)}`, auth.user.access_token, {
        method: 'DELETE',
        headers: { 'Idempotency-Key': newIdempotencyKey() },
      })
      await load()
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Role removal was rejected')
    }
  }

  async function suspend() {
    if (!auth.user?.access_token || !userId) {
      return
    }
    try {
      await api(`/api/v1/admin/users/${userId}/staff/suspend`, auth.user.access_token, {
        method: 'POST',
        headers: { 'Idempotency-Key': newIdempotencyKey() },
        body: '{}',
      })
      await load()
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Suspension was rejected')
    }
  }

  if (!detail) {
    return <main className="page">{error ? <p className="error">{error}</p> : <p>Loading…</p>}</main>
  }

  return (
    <main className="page">
      <h1>{detail.user.email || detail.user.displayName}</h1>
      {error ? <p className="error">{error}</p> : null}
      <p>Direct roles: {detail.directRoles.join(', ') || 'none'}</p>
      <p>Effective roles: {detail.effectiveRoles.join(', ') || 'none'}</p>
      <p>Staff: {detail.staffProfile ? detail.staffProfile.onboardingStatus : 'not onboarded'}</p>
      {canAssignRoles(permissions) ? (
        <form className="row" onSubmit={assign}>
          <input value={role} onChange={(event) => setRole(event.target.value)} />
          <button type="submit">Assign role</button>
        </form>
      ) : null}
      {canAssignRoles(permissions) ? (
        <ul className="list">
          {detail.directRoles.map((name) => (
            <li key={name}>
              {name}
              <button type="button" onClick={() => void remove(name)}>Remove</button>
            </li>
          ))}
        </ul>
      ) : null}
      {permissions.includes('PERM_user.manage_staff') ? (
        <button type="button" onClick={() => void suspend()}>Suspend staff access</button>
      ) : null}
    </main>
  )
}
