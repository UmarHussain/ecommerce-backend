import { useEffect, useRef, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  ORDER_POLL_LIMIT,
  ORDER_POLL_MS,
  canCancel,
  cancelOrder,
  getOrder,
  isTerminal,
  money,
  orderProgress,
  retryableStatus,
  type Order,
} from '../api/orders'

export function OrderPage() {
  const auth = useAuth()
  const { orderId } = useParams()
  const subject = auth.user?.profile.sub ?? null
  const token = auth.user?.access_token
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [stopped, setStopped] = useState(false)
  const [watch, setWatch] = useState(0)
  const seen = useRef(0)
  const identity = `${auth.isAuthenticated}:${subject ?? ''}:${orderId ?? ''}`
  const identityRef = useRef(identity)
  if (identityRef.current !== identity) {
    identityRef.current = identity
    seen.current += 1
  }

  useEffect(() => {
    setOrder(null)
    setError(null)
    setStopped(false)
  }, [auth.isAuthenticated, subject, orderId])

  useEffect(() => {
    if (!auth.isAuthenticated || !token || !subject || !orderId) {
      return
    }
    const accessToken = token
    const currentOrderId = orderId
    const ticket = seen.current
    let attempts = 0
    let timer = 0

    function schedule() {
      timer = window.setTimeout(() => {
        if (seen.current === ticket) void load()
      }, ORDER_POLL_MS)
    }

    async function load() {
      try {
        const loaded = await getOrder(accessToken, currentOrderId)
        if (seen.current !== ticket) return
        setOrder(loaded)
        setError(null)
        attempts += 1
        if (!isTerminal(loaded.orderStatus) && attempts < ORDER_POLL_LIMIT) {
          schedule()
        } else if (!isTerminal(loaded.orderStatus)) {
          setStopped(true)
        }
      } catch (cause: unknown) {
        if (seen.current !== ticket) return
        const retry = isUncertain(cause)
        attempts += 1
        setError(cause instanceof ApiError ? cause.message : 'Could not load the order')
        if (retry && attempts < ORDER_POLL_LIMIT) {
          schedule()
          return
        }
        if (retry) setStopped(true)
      }
    }

    void load()
    return () => {
      seen.current += 1
      window.clearTimeout(timer)
    }
  }, [auth.isAuthenticated, subject, token, orderId, watch])

  if (!auth.isAuthenticated) {
    return (
      <main className="page">
        <h1>Order</h1>
        <p>Sign in to see this order.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in</button>
      </main>
    )
  }

  async function cancel() {
    if (!token || !order || pending) return
    seen.current += 1
    setPending(true)
    try {
      setOrder(await cancelOrder(token, order.id))
      setError(null)
      setStopped(false)
      setWatch((value) => value + 1)
    } catch (cause: unknown) {
      setError(cause instanceof ApiError ? cause.message : 'Could not cancel the order')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="page">
      <p><Link to="/orders">All orders</Link></p>
      <h1>Order</h1>
      {error ? <p className="error" role="alert">{error}</p> : null}
      {!order && !error ? <p role="status">Loading order…</p> : null}
      {order ? (
        <>
          <p role="status">{orderProgress(order)}</p>
          {order.paymentSimulated ? <p>Payment is simulated. No card is charged.</p> : null}
          <p>Amount {money(order.grandTotal)} {order.currency}</p>
          <p>Payment {order.paymentStatus}</p>
          <p>Fulfilment {order.fulfilmentStatus}</p>
          {order.obligation ? <p>Outstanding: {order.obligation}</p> : null}
          <p>
            {order.address.label}: {order.address.line1}, {order.address.city} {order.address.postalCode} {order.address.countryCode}
          </p>
          <ul>
            {order.lines.map((line) => (
              <li key={line.sku}>{line.displayName} · {line.quantity} · {money(line.lineTotal)} {order.currency}</li>
            ))}
          </ul>
          {stopped ? <p role="status">Still in progress. Refresh the page to keep watching.</p> : null}
          {canCancel(order) ? (
            <button type="button" disabled={pending} onClick={() => void cancel()}>Cancel order</button>
          ) : null}
        </>
      ) : null}
    </main>
  )
}

function isUncertain(cause: unknown) {
  return !(cause instanceof ApiError) || retryableStatus(cause.status)
}
