import { api } from './client'

export interface Category {
  id: string
  name: string
  slug: string
  active: boolean
  version: number
}

export interface VariantPrice {
  id: string
  sku: string
  name: string
  price: string
  currency: string
  imageUrl: string | null
}

export interface ProductSummary {
  id: string
  name: string
  slug: string
  description: string | null
  active: boolean
  version: number
  category: Category
  variants: VariantPrice[]
}

export interface Variant {
  id: string
  productId: string
  sku: string
  name: string
  price: string
  currency: string
  imageUrl: string | null
  active: boolean
  version: number
}

export interface Product extends ProductSummary {
  variants: Variant[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  sort: string
}

export function listCategories(token: string, query: string) {
  return api<Page<Category>>(`/api/v1/admin/catalog/categories?${query}`, token)
}

export function getCategory(token: string, id: string) {
  return api<Category>(`/api/v1/admin/catalog/categories/${id}`, token)
}

export function createCategory(token: string, body: { name: string; slug: string }) {
  return api<Category>('/api/v1/admin/catalog/categories', token, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateCategory(token: string, id: string, body: { name: string; slug: string; expectedVersion: number }) {
  return api<Category>(`/api/v1/admin/catalog/categories/${id}`, token, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function setCategoryStatus(token: string, id: string, active: boolean, expectedVersion: number) {
  return api<Category>(`/api/v1/admin/catalog/categories/${id}/status`, token, {
    method: 'PATCH',
    body: JSON.stringify({ active, expectedVersion }),
  })
}

export function listProducts(token: string, query: string) {
  return api<Page<ProductSummary>>(`/api/v1/admin/catalog/products?${query}`, token)
}

export function getProduct(token: string, id: string) {
  return api<Product>(`/api/v1/admin/catalog/products/${id}`, token)
}

export function createProduct(token: string, body: { name: string; slug: string; description: string; categoryId: string }) {
  return api<Product>('/api/v1/admin/catalog/products', token, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateProduct(
  token: string,
  id: string,
  body: { name: string; slug: string; description: string; categoryId: string; expectedVersion: number },
) {
  return api<Product>(`/api/v1/admin/catalog/products/${id}`, token, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function setProductStatus(token: string, id: string, active: boolean, expectedVersion: number) {
  return api<Product>(`/api/v1/admin/catalog/products/${id}/status`, token, {
    method: 'PATCH',
    body: JSON.stringify({ active, expectedVersion }),
  })
}

export function listVariants(token: string, productId: string) {
  return api<Variant[]>(`/api/v1/admin/catalog/products/${productId}/variants`, token)
}

export function createVariant(
  token: string,
  productId: string,
  body: { sku: string; name: string; price: string; currency: string; imageUrl: string },
) {
  return api<Variant>(`/api/v1/admin/catalog/products/${productId}/variants`, token, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateVariant(
  token: string,
  variantId: string,
  body: { sku: string; name: string; price: string; currency: string; imageUrl: string; expectedVersion: number },
) {
  return api<Variant>(`/api/v1/admin/catalog/variants/${variantId}`, token, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function setVariantStatus(token: string, variantId: string, active: boolean, expectedVersion: number) {
  return api<Variant>(`/api/v1/admin/catalog/variants/${variantId}/status`, token, {
    method: 'PATCH',
    body: JSON.stringify({ active, expectedVersion }),
  })
}
