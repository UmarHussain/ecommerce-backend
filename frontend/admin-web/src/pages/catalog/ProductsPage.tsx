import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { listCategories, listProducts, type Category, type ProductSummary } from '../../api/catalog'
import { canCreateCatalog, canSeeCatalog } from '../../auth/permissions'
import { CatalogNotice, asApiError } from './CatalogNotice'
import type { ApiError } from '../../api/client'

export function ProductsPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [active, setActive] = useState('')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [sort, setSort] = useState('name,asc')
  const [page, setPage] = useState(0)
  const [categories, setCategories] = useState<Category[]>([])
  const [items, setItems] = useState<ProductSummary[]>([])
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (!canSeeCatalog(permissions) || !auth.user?.access_token) {
      return
    }
    listCategories(auth.user.access_token, 'page=0&size=100&sort=name,asc')
      .then((result) => setCategories(result.items))
      .catch(() => setCategories([]))
  }, [auth.user?.access_token, permissions])

  useEffect(() => {
    if (!canSeeCatalog(permissions) || !auth.user?.access_token) {
      return
    }
    const params = new URLSearchParams({ page: String(page), size: '20', sort })
    if (search.trim()) params.set('search', search.trim())
    if (categoryId) params.set('categoryId', categoryId)
    if (active) params.set('active', active)
    if (minPrice) params.set('minPrice', minPrice)
    if (maxPrice) params.set('maxPrice', maxPrice)
    if (minPrice || maxPrice) params.set('currency', currency.trim().toUpperCase())
    setLoading(true)
    listProducts(auth.user.access_token, params.toString())
      .then((result) => {
        setItems(result.items)
        setLast(result.last)
        setError(null)
      })
      .catch((cause: unknown) => setError(asApiError(cause, 'Could not load products')))
      .finally(() => setLoading(false))
  }, [auth.user?.access_token, permissions, search, categoryId, active, minPrice, maxPrice, currency, sort, page])

  if (!canSeeCatalog(permissions)) {
    return <main className="page"><h1>Products</h1><p className="error" role="alert">Catalog access requires catalog read permission.</p></main>
  }

  return (
    <main className="page">
      <h1>Products</h1>
      <form className="filters">
        <label>Search<input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label>
        <label>Category
          <select value={categoryId} onChange={(event) => { setCategoryId(event.target.value); setPage(0) }}>
            <option value="">All</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
        </label>
        <label>Status
          <select value={active} onChange={(event) => { setActive(event.target.value); setPage(0) }}>
            <option value="">All</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </label>
        <label>Sort
          <select value={sort} onChange={(event) => { setSort(event.target.value); setPage(0) }}>
            <option value="name,asc">Name A–Z</option>
            <option value="name,desc">Name Z–A</option>
            <option value="updatedAt,desc">Recently updated</option>
          </select>
        </label>
        <label>Min price<input inputMode="decimal" value={minPrice} onChange={(event) => { setMinPrice(event.target.value); setPage(0) }} /></label>
        <label>Max price<input inputMode="decimal" value={maxPrice} onChange={(event) => { setMaxPrice(event.target.value); setPage(0) }} /></label>
        <label>Currency
          <input value={currency} maxLength={3} onChange={(event) => { setCurrency(event.target.value); setPage(0) }} />
        </label>
        {canCreateCatalog(permissions) ? <Link to="/catalog/products/new">New product</Link> : null}
      </form>
      <p className="muted">Price filters compare the stored amount in the chosen currency. They do not convert between currencies.</p>
      <CatalogNotice error={error} loading={loading} empty={!loading && items.length === 0} />
      <ul className="list">
        {items.map((product) => (
          <li key={product.id}>
            <Link to={`/catalog/products/${product.id}`}>{product.name}</Link>
            <span>{product.active ? 'Active' : 'Inactive'} · {product.category.name}</span>
          </li>
        ))}
      </ul>
      <div className="actions">
        <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button type="button" disabled={last} onClick={() => setPage((current) => current + 1)}>Next</button>
      </div>
    </main>
  )
}
