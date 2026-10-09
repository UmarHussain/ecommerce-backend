import { AuthProvider } from 'react-oidc-context'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { oidcConfig } from './auth/oidc'
import { CallbackPage } from './pages/Callback'
import { CartPage } from './pages/CartPage'
import { CatalogPage } from './pages/CatalogPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { HomePage } from './pages/Home'
import { OrderPage } from './pages/OrderPage'
import { OrdersPage } from './pages/OrdersPage'
import { ProductPage } from './pages/ProductPage'
import { ProfilePage } from './pages/Profile'
import { SilentRenewPage } from './pages/SilentRenew'

export function App() {
  return (
    <AuthProvider {...oidcConfig}>
      <BrowserRouter>
        <header className="nav">
          <Link to="/">Storefront</Link>
          <Link to="/catalog">Catalog</Link>
          <Link to="/cart">Cart</Link>
          <Link to="/checkout">Checkout</Link>
          <Link to="/orders">Orders</Link>
          <Link to="/profile">Profile</Link>
        </header>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/catalog" element={<CatalogPage />} />
          <Route path="/catalog/:productId" element={<ProductPage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:orderId" element={<OrderPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/callback" element={<CallbackPage />} />
          <Route path="/silent-renew" element={<SilentRenewPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
