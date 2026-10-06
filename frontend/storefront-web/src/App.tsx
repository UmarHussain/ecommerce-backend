import { AuthProvider } from 'react-oidc-context'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { oidcConfig } from './auth/oidc'
import { CallbackPage } from './pages/Callback'
import { HomePage } from './pages/Home'
import { ProfilePage } from './pages/Profile'
import { SilentRenewPage } from './pages/SilentRenew'

export function App() {
  return (
    <AuthProvider {...oidcConfig}>
      <BrowserRouter>
        <header className="nav">
          <Link to="/">Storefront</Link>
          <Link to="/profile">Profile</Link>
        </header>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/callback" element={<CallbackPage />} />
          <Route path="/silent-renew" element={<SilentRenewPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
