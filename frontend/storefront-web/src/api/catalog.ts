import { api } from './client'

export interface Category {
  id: string
  name: string
  slug: string
  active: boolean
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
  category: Category
  variants: VariantPrice[]
}

export interface Variant {
  id: string
  sku: string
  name: string
  price: string
  currency: string
  imageUrl: string | null
  active: boolean
}

export interface Product {
  id: string
  name: string
  slug: string
  description: string | null
  active: boolean
  category: Category
  variants: Variant[]
}

export interface Page<T> {
  items: T[]
  page: number
  last: boolean
}

export function listCategories() {
  return api<Category[]>('/api/v1/store/catalog/categories', undefined)
}

export function listProducts(query: string) {
  return api<Page<ProductSummary>>(`/api/v1/store/catalog/products?${query}`, undefined)
}

export function getProduct(id: string) {
  return api<Product>(`/api/v1/store/catalog/products/${id}`, undefined)
}
