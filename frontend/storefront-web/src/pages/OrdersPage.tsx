import { useEffect, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { listOrders, money, orderProgress, type Order } from '../api/orders'

export function OrdersPage() {
  const auth = useAuth()
  const subject = auth.user?.profile.sub ?? null
  const token = auth.user?.access_token
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const seen = useRef(0)
  const identity = `${auth.isAuthenticated}:${subject ?? ''}`
  const identityRef = useRef(identity)
  if (identityRef.current !== identity) {
    identityRef.current = identity
    seen.current += 1
  }

  useEffect(() => {
    setOrders(null)
    setError(null)
    if (!auth.isAuthenticated || !token || !subject) {
      return
    }
    const accessToken = token
    const ticket = seen.current
    listOrders(accessToken)
      .then((page) => {
        if (seen.current === ticket) setOrders(page.items)
      })
      .catch((cause: unknown) => {
        if (seen.current === ticket) {
          setError(cause instanceof ApiError ? cause.message : 'Could not load orders')
        }
      })
    return () => {
      seen.current += 1
    }
  }, [auth.isAuthenticated, subject, token])

  if (!auth.isAuthenticated) {
    return (
      <main className="page">
        <h1>Orders</h1>
        <p>Sign in to see your orders.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>Orders</h1>
      {error ? <p className="error" role="alert">{error}</p> : null}
      {!orders && !error ? <p role="status">Loading orders…</p> : null}
      {orders && orders.length === 0 ? <p>You have no orders yet.</p> : null}
      {orders && orders.length > 0 ? (
        <ul className="list">
          {orders.map((order) => (
            <li key={order.id}>
              <Link to={`/orders/${encodeURIComponent(order.id)}`}>{money(order.grandTotal)} {order.currency}</Link>
              <div>{orderProgress(order)}</div>
              {order.obligation ? <div>{order.obligation}</div> : null}
              {order.paymentSimulated ? <div className="muted">Payment is simulated.</div> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </main>
  )
}
