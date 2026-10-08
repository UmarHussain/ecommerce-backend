import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from 'react-oidc-context'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { api } from './api/client'
import { oidcConfig } from './auth/oidc'
import { canSeeCatalog, canSeeRoles, canSeeUsers } from './auth/permissions'
import { CategoriesPage } from './pages/catalog/CategoriesPage'
import { CategoryPage } from './pages/catalog/CategoryPage'
import { ProductPage } from './pages/catalog/ProductPage'
import { ProductsPage } from './pages/catalog/ProductsPage'
import { CallbackPage } from './pages/Callback'
import { HomePage } from './pages/Home'
import { RolesPage } from './pages/Roles'
import { SilentRenewPage } from './pages/SilentRenew'
import { UserDetailPage } from './pages/UserDetail'
import { UsersPage } from './pages/Users'

interface MeResponse {
  permissions: string[]
}

function Shell() {
  const auth = useAuth()
  const [permissions, setPermissions] = useState<string[]>([])
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    if (!auth.isAuthenticated || !auth.user?.access_token) {
      setPermissions([])
      setDenied(false)
      return
    }
    api<MeResponse>('/api/v1/admin/me', auth.user.access_token)
      .then((me) => {
        setPermissions(me.permissions ?? [])
        setDenied(false)
      })
      .catch((cause: { status?: number }) => {
        setPermissions([])
        setDenied(cause.status === 403)
      })
  }, [auth.isAuthenticated, auth.user?.access_token])

  return (
    <>
      <header className="nav">
        <Link to="/">Administration</Link>
        {canSeeUsers(permissions) ? <Link to="/users">Users</Link> : null}
        {canSeeRoles(permissions) ? <Link to="/roles">Roles</Link> : null}
        {canSeeCatalog(permissions) ? <Link to="/catalog/categories">Categories</Link> : null}
        {canSeeCatalog(permissions) ? <Link to="/catalog/products">Products</Link> : null}
      </header>
      <Routes>
        <Route path="/" element={<HomePage permissions={permissions} denied={denied} />} />
        <Route path="/users" element={<UsersPage permissions={permissions} />} />
        <Route path="/users/:userId" element={<UserDetailPage permissions={permissions} />} />
        <Route path="/roles" element={<RolesPage permissions={permissions} />} />
        <Route path="/catalog/categories" element={<CategoriesPage permissions={permissions} />} />
        <Route path="/catalog/categories/new" element={<CategoryPage permissions={permissions} />} />
        <Route path="/catalog/categories/:categoryId" element={<CategoryPage permissions={permissions} />} />
        <Route path="/catalog/products" element={<ProductsPage permissions={permissions} />} />
        <Route path="/catalog/products/new" element={<ProductPage permissions={permissions} />} />
        <Route path="/catalog/products/:productId" element={<ProductPage permissions={permissions} />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route path="/silent-renew" element={<SilentRenewPage />} />
      </Routes>
    </>
  )
}

export function App() {
  return (
    <AuthProvider {...oidcConfig}>
      <BrowserRouter>
        <Shell />
      </BrowserRouter>
    </AuthProvider>
  )
}
