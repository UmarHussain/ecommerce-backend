import { api } from './client'

export interface CartItem {
  catalogVariantId: string
  sku: string
  quantity: number
  displayName: string
  imageUrl: string | null
  unitPrice: string
  currency: string
  snapshotAt: string
  lineTotal: string
  catalogState: 'CONFIRMED' | 'UNKNOWN' | 'UNAVAILABLE'
  currentUnitPrice: string | null
}

export interface Cart {
  version: number
  items: CartItem[]
  subtotals: Array<{ currency: string; amount: string }>
  catalogRefresh: 'FRESH' | 'UNKNOWN'
  checkoutNotice: string
}

export function getCart(token: string) {
  return api<Cart>('/api/v1/store/cart', token)
}

export function setQuantity(token: string, sku: string, quantity: number, expectedVersion: number) {
  return api<Cart>(`/api/v1/store/cart/items/${encodeURIComponent(sku)}`, token, {
    method: 'PUT',
    body: JSON.stringify({ quantity, expectedVersion }),
  })
}

export function removeItem(token: string, sku: string, expectedVersion: number) {
  return api<Cart>(`/api/v1/store/cart/items/${encodeURIComponent(sku)}?expectedVersion=${expectedVersion}`, token, {
    method: 'DELETE',
  })
}

export function clearCart(token: string, expectedVersion: number) {
  return api<Cart>(`/api/v1/store/cart?expectedVersion=${expectedVersion}`, token, {
    method: 'DELETE',
  })
}
