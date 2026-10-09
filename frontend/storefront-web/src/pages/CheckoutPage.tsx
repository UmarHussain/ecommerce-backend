import { useEffect, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useNavigate } from 'react-router-dom'
import { getCart, type Cart } from '../api/cart'
import { ApiError, api } from '../api/client'
import {
  acceptQuote,
  checkoutKeyFor,
  clearIdempotencyKey,
  createQuote,
  money,
  rememberCheckoutAttempt,
  retryableStatus,
  savedQuote,
  type Quote,
  type SavedAddress,
} from '../api/orders'

interface Profile {
  addresses: SavedAddress[]
}

export function CheckoutPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const subject = auth.user?.profile.sub ?? null
  const token = auth.user?.access_token
  const [cart, setCart] = useState<Cart | null>(null)
  const [addresses, setAddresses] = useState<SavedAddress[]>([])
  const [addressId, setAddressId] = useState('')
  const [quote, setQuote] = useState<Quote | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [label, setLabel] = useState('Home')
  const [line1, setLine1] = useState('')
  const [city, setCity] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [countryCode, setCountryCode] = useState('')
  const restoredSubject = useRef<string | null>(null)

  useEffect(() => {
    setCart(null)
    setError(null)
    setNotice(null)
    if (!auth.isAuthenticated || !subject) {
      setQuote(null)
      setAddresses([])
      setAddressId('')
      restoredSubject.current = null
      if (!subject) {
        clearIdempotencyKey()
      }
      return
    }
    if (restoredSubject.current !== subject) {
      restoredSubject.current = subject
      const restored = savedQuote(subject)
      setQuote(restored)
      setAddresses([])
      setAddressId(restored?.address.addressId ?? '')
    }
    if (!token) {
      return
    }
    let cancelled = false
    Promise.all([
      getCart(token),
      api<Profile>('/api/v1/store/me', token),
    ]).then(([loadedCart, profile]) => {
      if (cancelled) return
      setCart(loadedCart)
      setAddresses(profile.addresses)
      setAddressId((current) => current || profile.addresses[0]?.id || '')
    }).catch((cause: unknown) => {
      if (!cancelled) {
        setError(cause instanceof ApiError ? cause.message : 'Could not load checkout')
      }
    })
    return () => {
      cancelled = true
    }
  }, [auth.isAuthenticated, subject, token])

  if (!auth.isAuthenticated) {
    return (
      <main className="page">
        <h1>Checkout</h1>
        <p>Sign in to review a quote. Browsing the catalog stays available without an account.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
      </main>
    )
  }

  async function saveAddress(event: React.FormEvent) {
    event.preventDefault()
    if (!token || pending) return
    setPending(true)
    setError(null)
    try {
      const saved = await api<SavedAddress>('/api/v1/store/me/addresses', token, {
        method: 'POST',
        body: JSON.stringify({ label, line1, city, postalCode, countryCode }),
      })
      setAddresses((current) => [...current, saved])
      setAddressId(saved.id)
      setQuote(null)
      clearIdempotencyKey()
      setLine1('')
      setCity('')
      setPostalCode('')
      setCountryCode('')
    } catch (cause: unknown) {
      setError(cause instanceof ApiError ? cause.message : 'Could not save the address')
    } finally {
      setPending(false)
    }
  }

  async function review() {
    if (!token || !subject || !cart || !addressId || pending) return
    setPending(true)
    setError(null)
    setNotice(null)
    try {
      const created = await createQuote(token, cart.version, addressId)
      rememberCheckoutAttempt(subject, created)
      setQuote(created)
    } catch (cause: unknown) {
      if (cause instanceof ApiError && cause.code === 'ORDER_REVIEW_REQUIRED') {
        clearIdempotencyKey()
        setQuote(null)
      }
      setError(reviewMessage(cause))
    } finally {
      setPending(false)
    }
  }

  function chooseAddress(nextAddressId: string) {
    setAddressId(nextAddressId)
    setQuote(null)
    clearIdempotencyKey()
  }

  async function placeOrder() {
    if (!token || !subject || !quote || pending) return
    const key = checkoutKeyFor(subject, quote.id)
    if (!key) {
      setQuote(null)
      setError('Review the quote again before placing the order.')
      return
    }
    setPending(true)
    setNotice(null)
    try {
      const accepted = await acceptQuote(token, quote.id, key)
      clearIdempotencyKey()
      navigate(`/orders/${encodeURIComponent(accepted.id)}`)
    } catch (cause: unknown) {
      if (cause instanceof ApiError && cause.code === 'ORDER_REVIEW_REQUIRED') {
        clearIdempotencyKey()
        setQuote(null)
        setError(`${cause.message} Request a new quote before placing the order.`)
        return
      }
      if (isUncertain(cause)) {
        setNotice('The order was not confirmed. Retry uses the same checkout key.')
        return
      }
      setError(cause instanceof ApiError ? cause.message : 'Could not place the order')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="page">
      <h1>Checkout</h1>
      <p className="muted">The quote is the amount that will be frozen. Payment on the next step is simulated.</p>
      {notice ? (
        <p role="status">
          {notice} <Link to="/orders">Open Orders</Link> if it was already accepted.
        </p>
      ) : null}
      {error ? <p className="error" role="alert">{error}</p> : null}
      {!cart && !error ? <p role="status">Loading checkout…</p> : null}
      {cart && cart.items.length === 0 ? <p>Your cart is empty. <Link to="/catalog">Browse the catalog</Link>.</p> : null}
      {cart && cart.items.length > 0 ? (
        <>
          <h2>Cart version {cart.version}</h2>
          <ul className="list">
            {cart.items.map((item) => (
              <li key={item.sku}>{item.displayName} · {item.quantity} · {item.unitPrice} {item.currency}</li>
            ))}
          </ul>
          {addresses.length > 0 ? (
            <label>
              Ship to
              <select aria-label="Ship to" value={addressId} onChange={(event) => chooseAddress(event.target.value)}>
                {addresses.map((address) => (
                  <option key={address.id} value={address.id}>
                    {address.label}: {address.line1}, {address.city} {address.postalCode} {address.countryCode}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <form onSubmit={saveAddress} className="stack">
              <p>Save an address before requesting a quote.</p>
              <label>Label<input aria-label="Address label" value={label} onChange={(event) => setLabel(event.target.value)} /></label>
              <label>Line 1<input aria-label="Address line 1" value={line1} onChange={(event) => setLine1(event.target.value)} /></label>
              <label>City<input aria-label="City" value={city} onChange={(event) => setCity(event.target.value)} /></label>
              <label>Postal code<input aria-label="Postal code" value={postalCode} onChange={(event) => setPostalCode(event.target.value)} /></label>
              <label>Country<input aria-label="Country" value={countryCode} onChange={(event) => setCountryCode(event.target.value)} maxLength={2} /></label>
              <button type="submit" disabled={pending}>Save address</button>
            </form>
          )}
          <button type="button" disabled={pending || !addressId} onClick={() => void review()}>Review quote</button>
        </>
      ) : null}
      {quote ? (
        <section aria-label="Quote">
          <h2>Quote</h2>
          <p>Amount {money(quote.grandTotal)} {quote.currency}</p>
          <p>Merchandise {money(quote.merchandiseTotal)} {quote.currency}</p>
          <p>Shipping {money(quote.shippingTotal)} ({quote.shippingPolicy})</p>
          <p>Tax {money(quote.taxTotal)} ({quote.taxPolicy})</p>
          <p>Expires {quote.expiresAt}</p>
          <p>
            {quote.address.label}: {quote.address.line1}, {quote.address.city} {quote.address.postalCode} {quote.address.countryCode}
          </p>
          <ul>
            {quote.lines.map((line) => (
              <li key={line.sku}>{line.displayName} · {line.quantity} · {money(line.lineTotal)} {quote.currency}</li>
            ))}
          </ul>
          <button type="button" disabled={pending} onClick={() => void placeOrder()}>Place order</button>
        </section>
      ) : null}
    </main>
  )
}

function isUncertain(cause: unknown) {
  return !(cause instanceof ApiError) || retryableStatus(cause.status)
}

function reviewMessage(cause: unknown) {
  if (!(cause instanceof ApiError)) return 'Could not create a quote'
  if (cause.code === 'ORDER_REVIEW_REQUIRED') {
    return `${cause.message} Request a new quote after you review the cart.`
  }
  return cause.message
}
