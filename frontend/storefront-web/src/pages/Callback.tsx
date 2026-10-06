import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { useNavigate } from 'react-router-dom'

export function CallbackPage() {
  const auth = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (auth.isAuthenticated) {
      navigate('/', { replace: true })
    }
  }, [auth.isAuthenticated, navigate])

  if (auth.error) {
    return <main className="page"><p className="error">Sign-in failed: {auth.error.message}</p></main>
  }
  return <main className="page"><p>Completing sign-in…</p></main>
}
