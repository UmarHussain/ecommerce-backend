import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { listCategories, type Category } from '../../api/catalog'
import { canCreateCatalog, canSeeCatalog } from '../../auth/permissions'
import { CatalogNotice, asApiError } from './CatalogNotice'
import type { ApiError } from '../../api/client'

export function CategoriesPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [search, setSearch] = useState('')
  const [active, setActive] = useState('')
  const [page, setPage] = useState(0)
  const [items, setItems] = useState<Category[]>([])
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (!canSeeCatalog(permissions) || !auth.user?.access_token) {
      return
    }
    const token = auth.user.access_token
    const params = new URLSearchParams({ page: String(page), size: '20', sort: 'name,asc' })
    if (search.trim()) {
      params.set('search', search.trim())
    }
    if (active) {
      params.set('active', active)
    }
    setLoading(true)
    listCategories(token, params.toString())
      .then((result) => {
        setItems(result.items)
        setLast(result.last)
        setError(null)
      })
      .catch((cause: unknown) => setError(asApiError(cause, 'Could not load categories')))
      .finally(() => setLoading(false))
  }, [auth.user?.access_token, permissions, search, active, page])

  if (!canSeeCatalog(permissions)) {
    return <main className="page"><h1>Categories</h1><p className="error" role="alert">Catalog access requires catalog read permission.</p></main>
  }

  return (
    <main className="page">
      <h1>Categories</h1>
      <form className="filters" onSubmit={(event) => { event.preventDefault(); setPage(0) }}>
        <label>Search<input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label>
        <label>Status
          <select value={active} onChange={(event) => { setActive(event.target.value); setPage(0) }}>
            <option value="">All</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </label>
        {canCreateCatalog(permissions) ? <Link to="/catalog/categories/new">New category</Link> : null}
      </form>
      <CatalogNotice error={error} loading={loading} empty={!loading && items.length === 0} />
      <ul className="list">
        {items.map((category) => (
          <li key={category.id}>
            <Link to={`/catalog/categories/${category.id}`}>{category.name}</Link>
            <span>{category.active ? 'Active' : 'Inactive'}</span>
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
