import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { ApiError, api, newIdempotencyKey } from '../api/client'
import { canCreateUsers } from '../auth/permissions'

interface UserSummary {
  id: string
  email: string
  displayName: string
  enabled: boolean
  staff: boolean
  directRoles: string[]
}

export function UsersPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<UserSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')

  async function load() {
    if (!auth.user?.access_token) {
      return
    }
    try {
      const page = await api<{ items: UserSummary[] }>(
        `/api/v1/admin/users?q=${encodeURIComponent(query)}`,
        auth.user.access_token
      )
      setItems(page.items)
      setError(null)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Could not load users')
    }
  }

  useEffect(() => {
    void load()
  }, [auth.user?.access_token])

  async function createUser(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) {
      return
    }
    try {
      await api('/api/v1/admin/users', auth.user.access_token, {
        method: 'POST',
        headers: { 'Idempotency-Key': newIdempotencyKey() },
        body: JSON.stringify({
          email,
          temporaryPassword,
          roles: ['CUSTOMER'],
          onboardAsStaff: false,
        }),
      })
      setEmail('')
      setTemporaryPassword('')
      await load()
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Could not create user')
    }
  }

  return (
    <main className="page">
      <h1>Users</h1>
      {error ? <p className="error">{error}</p> : null}
      <form className="row" onSubmit={(event) => { event.preventDefault(); void load() }}>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search email or name" />
        <button type="submit">Search</button>
      </form>
      <ul className="list">
        {items.map((user) => (
          <li key={user.id}>
            <Link to={`/users/${user.id}`}>{user.email || user.displayName}</Link>
            <span>{user.directRoles.join(', ')}</span>
          </li>
        ))}
      </ul>
      {canCreateUsers(permissions) ? (
        <form className="stack" onSubmit={createUser}>
          <h2>Create user</h2>
          <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="email" required />
          <input value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} placeholder="temporary password" type="password" required minLength={8} />
          <button type="submit">Create customer</button>
        </form>
      ) : null}
    </main>
  )
}
