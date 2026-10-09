import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { clearCart, getCart, removeItem, setQuantity, type Cart } from '../api/cart'
import { ApiError } from '../api/client'

export function CartPage() {
  const auth = useAuth()
  const subject = auth.user?.profile.sub ?? null
  const token = auth.user?.access_token
  const [cart, setCart] = useState<Cart | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    setCart(null)
    setDrafts({})
    setError(null)
    setNotice(null)
    if (!auth.isAuthenticated || !token || !subject) {
      return
    }
    let cancelled = false
    getCart(token)
      .then((loaded) => {
        if (!cancelled) {
          setCart(loaded)
          setDrafts(draftsFrom(loaded))
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setError(cause instanceof ApiError ? cause : new ApiError(0, 'Could not load the cart'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [auth.isAuthenticated, subject, token])

  async function reload() {
    if (!token) return
    const loaded = await getCart(token)
    setCart(loaded)
    setDrafts(draftsFrom(loaded))
    setError(null)
    setNotice(null)
  }

  async function run(action: () => Promise<Cart>) {
    if (!token || pending) return
    setPending(true)
    setNotice(null)
    try {
      const updated = await action()
      setCart(updated)
      setDrafts(draftsFrom(updated))
      setError(null)
    } catch (cause: unknown) {
      if (cause instanceof ApiError && cause.status === 409) {
        setError(cause)
        setNotice('The cart changed since it was loaded. Your draft is still here.')
        return
      }
      if (!(cause instanceof ApiError) || cause.status === 0) {
        setNotice('The update was not confirmed. The cart was reloaded from the server.')
        try {
          await reload()
        } catch (reloadCause: unknown) {
          setError(reloadCause instanceof ApiError ? reloadCause : new ApiError(0, 'Could not reload the cart'))
        }
        return
      }
      setError(cause)
    } finally {
      setPending(false)
    }
  }

  if (!auth.isAuthenticated) {
    return (
      <main className="page">
        <h1>Cart</h1>
        <p>Sign in to use your cart. Browsing the catalog stays available without an account.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
      </main>
    )
  }
  if (!token) {
    return <main className="page"><p role="status">Loading your signed-in session…</p></main>
  }

  return (
    <main className="page">
      <h1>Cart</h1>
      {cart ? <p className="muted">{cart.checkoutNotice}</p> : null}
      {cart?.catalogRefresh === 'UNKNOWN' ? (
        <p role="status">Catalog could not be checked. Prices and availability below are the last stored snapshots.</p>
      ) : null}
      {notice ? <p role="status">{notice}</p> : null}
      {error ? (
        <div className="error" role="alert">
          <p>{messageFor(error)}</p>
          {error.correlationId ? <p>Reference {error.correlationId}</p> : null}
          {error.status === 409 ? <button type="button" onClick={() => void reload().catch(() => undefined)}>Reload and review</button> : null}
        </div>
      ) : null}
      {!cart && !error ? <p role="status">Loading cart…</p> : null}
      {cart && cart.items.length === 0 ? <p>Your cart is empty.</p> : null}
      {cart && cart.items.length > 0 ? (
        <>
          <ul className="list">
            {cart.items.map((item) => (
              <li key={item.sku}>
                <div>
                  <strong>{item.displayName}</strong>
                  <div className="muted">{item.sku}</div>
                  <div>{item.unitPrice} {item.currency}</div>
                  {item.catalogState === 'UNAVAILABLE' ? <div>This item is no longer available. It stays here until you remove it.</div> : null}
                  {item.catalogState === 'UNKNOWN' ? <div>Price and availability were not rechecked.</div> : null}
                  {item.catalogState === 'CONFIRMED' && item.currentUnitPrice && item.currentUnitPrice !== item.unitPrice ? (
                    <div>Catalog now shows {item.currentUnitPrice} {item.currency}. Checkout will validate the price again.</div>
                  ) : null}
                </div>
                <div className="actions">
                  <label>
                    Quantity
                    <input
                      aria-label={`Quantity for ${item.sku}`}
                      value={drafts[item.sku] ?? String(item.quantity)}
                      onChange={(event) => setDrafts((current) => ({ ...current, [item.sku]: event.target.value }))}
                      inputMode="numeric"
                    />
                  </label>
                  <button
                    type="button"
                    disabled={pending}
                    onClick={() => void run(() => setQuantity(token, item.sku, Number(drafts[item.sku]), cart.version))}
                  >
                    Update
                  </button>
                  <button type="button" disabled={pending} onClick={() => void run(() => removeItem(token, item.sku, cart.version))}>
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <div>
            {cart.subtotals.map((subtotal) => (
              <p key={subtotal.currency}>Display subtotal {subtotal.amount} {subtotal.currency}</p>
            ))}
          </div>
          <button type="button" disabled={pending} onClick={() => void run(() => clearCart(token, cart.version))}>
            Clear cart
          </button>
        </>
      ) : null}
    </main>
  )
}

function draftsFrom(cart: Cart) {
  return Object.fromEntries(cart.items.map((item) => [item.sku, String(item.quantity)]))
}

function messageFor(error: ApiError) {
  if (error.status === 401) return 'Sign in again to use your cart.'
  if (error.status === 403) return 'This account cannot change the cart.'
  if (error.status === 409) return 'The cart changed since it was loaded.'
  if (error.status === 503 || error.status === 504) return 'Catalog is unavailable, so this change was not saved.'
  return error.message
}
