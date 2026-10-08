import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listCategories, listProducts, type Category, type ProductSummary } from '../api/catalog'
import { ApiError } from '../api/client'

export function CatalogPage() {
  const [categories, setCategories] = useState<Category[]>([])
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [sort, setSort] = useState('name,asc')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [page, setPage] = useState(0)
  const [items, setItems] = useState<ProductSummary[]>([])
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    listCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), size: '12', sort })
    if (search.trim()) params.set('search', search.trim())
    if (category) params.set('category', category)
    if (minPrice) params.set('minPrice', minPrice)
    if (maxPrice) params.set('maxPrice', maxPrice)
    if (minPrice || maxPrice) params.set('currency', currency.trim().toUpperCase())
    setLoading(true)
    listProducts(params.toString())
      .then((result) => {
        setItems(result.items)
        setLast(result.last)
        setError(null)
      })
      .catch((cause: unknown) => {
        setItems([])
        setError(cause instanceof ApiError ? cause : new ApiError(0, 'Could not load the catalog'))
      })
      .finally(() => setLoading(false))
  }, [search, category, sort, minPrice, maxPrice, currency, page])

  return (
    <main className="page">
      <h1>Catalog</h1>
      <p>Browse without signing in. Inactive products stay hidden.</p>
      <form className="filters">
        <label>Search<input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label>
        <label>Category
          <select value={category} onChange={(event) => { setCategory(event.target.value); setPage(0) }}>
            <option value="">All</option>
            {categories.map((item) => <option key={item.id} value={item.slug}>{item.name}</option>)}
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
        <label>Currency<input value={currency} maxLength={3} onChange={(event) => { setCurrency(event.target.value); setPage(0) }} /></label>
      </form>
      <p className="muted">Price filters compare the stored amount in one currency and do not convert.</p>
      {loading ? <p role="status">Loading catalog…</p> : null}
      {error ? (
        <div className="error" role="alert">
          <p>{error.status === 503 || error.status === 502 || error.status === 504 ? 'Catalog is unavailable.' : error.message}</p>
          {error.correlationId ? <p>Reference {error.correlationId}</p> : null}
        </div>
      ) : null}
      {!loading && !error && items.length === 0 ? <p>No active products match these filters.</p> : null}
      <ul className="list">
        {items.map((product) => (
          <li key={product.id}>
            <Link to={`/catalog/${product.id}`}>{product.name}</Link>
            <span>{product.category.name}</span>
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
