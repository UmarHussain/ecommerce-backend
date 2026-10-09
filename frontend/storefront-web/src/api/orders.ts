import { api } from './client'

export interface AddressSnapshot {
  addressId: string
  label: string
  line1: string
  line2: string | null
  city: string
  region: string | null
  postalCode: string
  countryCode: string
}

export interface QuoteLine {
  catalogVariantId: string
  sku: string
  displayName: string
  quantity: number
  unitPrice: string | number
  lineTotal: string | number
}

export interface Quote {
  id: string
  cartVersion: number
  currency: string
  merchandiseTotal: string | number
  shippingTotal: string | number
  taxTotal: string | number
  grandTotal: string | number
  shippingPolicy: string
  taxPolicy: string
  expiresAt: string
  address: AddressSnapshot
  lines: QuoteLine[]
}

export type OrderStatus =
  | 'PENDING_STOCK'
  | 'PENDING_HOLD'
  | 'PENDING_PAYMENT'
  | 'PENDING_CONSUMPTION'
  | 'CONFIRMED'
  | 'COMPENSATING'
  | 'CANCEL_PENDING'
  | 'REJECTED'
  | 'CANCELLED'
  | 'MANUAL_REVIEW'

export interface Order {
  id: string
  orderStatus: OrderStatus
  paymentStatus: string
  fulfilmentStatus: string
  sagaStep: string
  cancellationRequested: boolean
  cleanupStatus: string
  obligation: string
  currency: string
  merchandiseTotal: string | number
  shippingTotal: string | number
  taxTotal: string | number
  grandTotal: string | number
  shippingPolicy: string
  taxPolicy: string
  paymentSimulated: boolean
  quoteId: string
  cartVersion: number
  createdAt: string
  updatedAt: string
  address: AddressSnapshot
  lines: QuoteLine[]
}

export interface OrderPage {
  items: Order[]
  page: number
  size: number
  total: number
}

export interface SavedAddress {
  id: string
  label: string
  line1: string
  line2: string | null
  city: string
  region: string | null
  postalCode: string
  countryCode: string
}

export const CHECKOUT_ATTEMPT_STORAGE = 'storefront.checkout.idempotency'

interface CheckoutAttempt {
  subject: string
  key: string
  quote: Quote
}

export function money(value: string | number) {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) {
    return String(value)
  }
  return numeric.toFixed(2)
}

export function createQuote(token: string, expectedCartVersion: number, addressId: string) {
  return api<Quote>('/api/v1/store/orders/quotes', token, {
    method: 'POST',
    body: JSON.stringify({ expectedCartVersion, addressId }),
  })
}

export function acceptQuote(token: string, quoteId: string, idempotencyKey: string) {
  return api<Order>('/api/v1/store/orders', token, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ quoteId }),
  })
}

export function listOrders(token: string) {
  return api<OrderPage>('/api/v1/store/orders', token)
}

export function getOrder(token: string, orderId: string) {
  return api<Order>(`/api/v1/store/orders/${encodeURIComponent(orderId)}`, token)
}

export function cancelOrder(token: string, orderId: string) {
  return api<Order>(`/api/v1/store/orders/${encodeURIComponent(orderId)}/cancel`, token, { method: 'POST' })
}

export function rememberCheckoutAttempt(subject: string, quote: Quote) {
  const current = readAttempt()
  const key = current?.subject === subject && current.quote.id === quote.id
    ? current.key
    : crypto.randomUUID()
  const attempt: CheckoutAttempt = { subject, key, quote }
  sessionStorage.setItem(CHECKOUT_ATTEMPT_STORAGE, JSON.stringify(attempt))
  return key
}

export function checkoutKeyFor(subject: string, quoteId: string) {
  const current = readAttempt()
  if (current?.subject === subject && current.quote.id === quoteId) {
    return current.key
  }
  return null
}

export function savedQuote(subject: string) {
  const current = readAttempt()
  if (!current) {
    return null
  }
  if (current.subject !== subject) {
    clearIdempotencyKey()
    return null
  }
  return current.quote
}

export function clearIdempotencyKey() {
  sessionStorage.removeItem(CHECKOUT_ATTEMPT_STORAGE)
}

function readAttempt(): CheckoutAttempt | null {
  const raw = sessionStorage.getItem(CHECKOUT_ATTEMPT_STORAGE)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<CheckoutAttempt>
    if (parsed.subject && parsed.key && parsed.quote && typeof parsed.quote.id === 'string') {
      return { subject: parsed.subject, key: parsed.key, quote: parsed.quote }
    }
  } catch {
    // Replace a corrupt attempt so the next checkout can store a new key.
  }
  sessionStorage.removeItem(CHECKOUT_ATTEMPT_STORAGE)
  return null
}

export function orderProgress(order: Order) {
  switch (order.orderStatus) {
    case 'PENDING_STOCK':
      return 'Waiting for stock to be reserved.'
    case 'PENDING_HOLD':
      return 'Stock is reserved. Holding it for the simulated payment.'
    case 'PENDING_PAYMENT':
      return 'Waiting for the simulated payment.'
    case 'PENDING_CONSUMPTION':
      return 'Simulated payment succeeded. Waiting for stock to be taken.'
    case 'CONFIRMED':
      return 'Confirmed. The simulated payment and stock consumption both finished.'
    case 'COMPENSATING':
      return 'Reversing the checkout. This stays pending until each reversal is acknowledged.'
    case 'CANCEL_PENDING':
      return 'Cancellation is in progress.'
    case 'REJECTED':
      return 'Rejected. Nothing was charged.'
    case 'CANCELLED':
      return 'Cancelled after the required reversals were acknowledged.'
    case 'MANUAL_REVIEW':
      return 'Needs review. Outstanding stock or payment obligations are still recorded.'
    default:
      return order.orderStatus
  }
}

export function isTerminal(status: OrderStatus) {
  return status === 'CONFIRMED' || status === 'REJECTED' || status === 'CANCELLED' || status === 'MANUAL_REVIEW'
}

export function canCancel(order: Order) {
  return order.orderStatus !== 'REJECTED'
    && order.orderStatus !== 'CANCELLED'
    && !order.cancellationRequested
}

export const ORDER_POLL_MS = 2000
export const ORDER_POLL_LIMIT = 40

export function retryableStatus(status: number) {
  return status === 0 || status === 408 || status === 429 || status >= 500
}
