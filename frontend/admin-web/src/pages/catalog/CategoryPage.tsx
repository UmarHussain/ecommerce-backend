import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import {
  createCategory,
  getCategory,
  setCategoryStatus,
  updateCategory,
  type Category,
} from '../../api/catalog'
import { canActivateCatalog, canCreateCatalog, canSeeCatalog, canUpdateCatalog } from '../../auth/permissions'
import { CatalogNotice, asApiError } from './CatalogNotice'
import type { ApiError } from '../../api/client'

export function CategoryPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const { categoryId } = useParams()
  const creating = !categoryId
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [loaded, setLoaded] = useState<Category | null>(null)
  const [loading, setLoading] = useState(!creating)
  const [error, setError] = useState<ApiError | null>(null)
  const [conflict, setConflict] = useState<ApiError | null>(null)

  function apply(category: Category) {
    setLoaded(category)
    setName(category.name)
    setSlug(category.slug)
    setConflict(null)
  }

  async function reload() {
    if (!categoryId || !auth.user?.access_token) {
      return
    }
    setLoading(true)
    try {
      apply(await getCategory(auth.user.access_token, categoryId))
      setError(null)
    } catch (cause) {
      setError(asApiError(cause, 'Could not load the category'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (creating || !canSeeCatalog(permissions)) {
      return
    }
    void reload()
  }, [categoryId, auth.user?.access_token, permissions])

  if (!canSeeCatalog(permissions)) {
    return <main className="page"><h1>Category</h1><p className="error" role="alert">Catalog access requires catalog read permission.</p></main>
  }
  if (creating && !canCreateCatalog(permissions)) {
    return <main className="page"><h1>New category</h1><p className="error" role="alert">Creating a category requires catalog create permission.</p></main>
  }

  const editable = creating ? canCreateCatalog(permissions) : canUpdateCatalog(permissions)

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) {
      return
    }
    setError(null)
    try {
      if (creating) {
        const created = await createCategory(auth.user.access_token, { name, slug })
        navigate(`/catalog/categories/${created.id}`)
        return
      }
      if (!loaded) {
        return
      }
      apply(await updateCategory(auth.user.access_token, loaded.id, {
        name,
        slug,
        expectedVersion: loaded.version,
      }))
    } catch (cause) {
      const problem = asApiError(cause, 'Could not save the category')
      if (problem.status === 409) {
        setConflict(problem)
        return
      }
      setError(problem)
    }
  }

  async function changeActive(active: boolean) {
    if (!loaded || !auth.user?.access_token) {
      return
    }
    try {
      apply(await setCategoryStatus(auth.user.access_token, loaded.id, active, loaded.version))
      setError(null)
    } catch (cause) {
      const problem = asApiError(cause, 'Could not change activation')
      if (problem.status === 409) {
        setConflict(problem)
        return
      }
      setError(problem)
    }
  }

  return (
    <main className="page">
      <p><Link to="/catalog/categories">Categories</Link></p>
      <h1>{creating ? 'New category' : name || 'Category'}</h1>
      <CatalogNotice error={error} loading={loading} />
      {conflict ? (
        <div className="banner" role="alert">
          <p>{conflict.message}</p>
          {conflict.correlationId ? <p>Reference {conflict.correlationId}</p> : null}
          <button type="button" onClick={() => void reload()}>Reload and review</button>
        </div>
      ) : null}
      <form className="stack" onSubmit={(event) => void save(event)}>
        <label>Name<input value={name} onChange={(event) => setName(event.target.value)} required disabled={!editable} /></label>
        <label>Slug<input value={slug} onChange={(event) => setSlug(event.target.value)} required disabled={!editable} /></label>
        {loaded ? <p>{loaded.active ? 'Active' : 'Inactive'} · version {loaded.version}</p> : null}
        {editable ? <button type="submit">{creating ? 'Create category' : 'Save category'}</button> : <p>This account can view the category and cannot edit it.</p>}
      </form>
      {loaded && canActivateCatalog(permissions) ? (
        <div className="actions">
          <button type="button" onClick={() => void changeActive(!loaded.active)}>
            {loaded.active ? 'Deactivate' : 'Activate'}
          </button>
        </div>
      ) : null}
    </main>
  )
}
