import { useEffect } from 'react'
import { UserManager } from 'oidc-client-ts'
import { userManagerSettings } from '../auth/oidc'

export function SilentRenewPage() {
  useEffect(() => {
    void new UserManager(userManagerSettings).signinSilentCallback()
  }, [])
  return <main className="page"><p>Renewing session…</p></main>
}
