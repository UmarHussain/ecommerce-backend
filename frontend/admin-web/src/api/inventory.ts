import { api, ApiError, newIdempotencyKey } from './client'

export interface StockItem {
  id: string
  catalogVariantId: string
  sku: string
  onHand: number
  reserved: number
  available: number
  version: number
  productNameSnapshot: string | null
  variantNameSnapshot: string | null
}

export interface StockAdjustment {
  id: string
  stockItemId: string
  operationType: string
  delta: number
  beforeOnHand: number
  afterOnHand: number
  reservedSnapshot: number
  resultingVersion: number
  reasonCode: string
  note: string | null
  reference: string | null
  actorIssuer: string
  actorSubject: string
  createdAt: string
}

export interface StockPage<T> {
  items: T[]
  page: number
  totalElements: number
  last: boolean
  sort: string
}

export interface StockCommandResult {
  stockItem: StockItem
  adjustment: StockAdjustment
}

export function listStock(token: string, search: string, page: number) {
  const params = new URLSearchParams({ page: String(page), size: '20', sort: 'sku,asc' })
  if (search.trim()) {
    params.set('search', search.trim())
  }
  return api<StockPage<StockItem>>(`/api/v1/admin/inventory/stock-items?${params}`, token)
}

export function getStock(token: string, id: string) {
  return api<StockItem>(`/api/v1/admin/inventory/stock-items/${id}`, token)
}

export function stockHistory(token: string, id: string) {
  return api<StockPage<StockAdjustment>>(`/api/v1/admin/inventory/stock-items/${id}/adjustments?sort=createdAt,desc`, token)
}

export function setupStock(token: string, key: string, body: string) {
  return api<StockCommandResult>('/api/v1/admin/inventory/stock-items', token, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body,
  })
}

export function adjustStock(token: string, id: string, key: string, body: string) {
  return api<StockCommandResult>(`/api/v1/admin/inventory/stock-items/${id}/adjustments`, token, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body,
  })
}

export { ApiError, newIdempotencyKey }
