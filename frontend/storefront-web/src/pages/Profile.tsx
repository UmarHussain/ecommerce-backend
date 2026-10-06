import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { ApiError, api } from '../api/client'

interface Profile {
  id: string
  email: string
  displayName: string
  preferences: Record<string, unknown>
  addresses: Array<{
    id: string
    label: string
    line1: string
    city: string
    postalCode: string
    countryCode: string
  }>
  staffProfile: { department: string | null; onboardingStatus: string } | null
  profilePersistenceImplemented: boolean
}

export function ProfilePage() {
  const auth = useAuth()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState('')

  useEffect(() => {
    if (!auth.isAuthenticated || !auth.user?.access_token) {
      return
    }
    api<Profile>('/api/v1/store/me', auth.user.access_token)
      .then((data) => {
        setProfile(data)
        setDisplayName(data.displayName ?? '')
      })
      .catch((cause: unknown) => {
        setError(cause instanceof ApiError ? cause.message : 'Could not load profile')
      })
  }, [auth.isAuthenticated, auth.user?.access_token])

  if (!auth.isAuthenticated) {
    return (
      <main className="page">
        <h1>Your profile</h1>
        <p>Sign in with Keycloak to create your application profile. Passwords stay in Keycloak.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
      </main>
    )
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) {
      return
    }
    try {
      const updated = await api<Profile>('/api/v1/store/me', auth.user.access_token, {
        method: 'PATCH',
        body: JSON.stringify({ displayName }),
      })
      setProfile(updated)
      setError(null)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Could not save profile')
    }
  }

  return (
    <main className="page">
      <h1>Your profile</h1>
      {error ? <p className="error">{error}</p> : null}
      {profile ? (
        <form onSubmit={save} className="stack">
          <p>Application user id: {profile.id}</p>
          <p>Email snapshot: {profile.email}</p>
          <label>
            Display name
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          </label>
          <button type="submit">Save</button>
          {profile.staffProfile ? (
            <p>Staff onboarding: {profile.staffProfile.onboardingStatus} ({profile.staffProfile.department || 'no department'})</p>
          ) : null}
        </form>
      ) : (
        <p>Loading profile…</p>
      )}
    </main>
  )
}
