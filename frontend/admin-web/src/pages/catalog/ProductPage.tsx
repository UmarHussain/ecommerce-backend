import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import {
  createProduct,
  createVariant,
  getProduct,
  listCategories,
  listVariants,
  setProductStatus,
  setVariantStatus,
  updateProduct,
  updateVariant,
  type Category,
  type Product,
  type Variant,
} from '../../api/catalog'
import { canActivateCatalog, canCreateCatalog, canSeeCatalog, canUpdateCatalog } from '../../auth/permissions'
import { CatalogNotice, asApiError } from './CatalogNotice'
import type { ApiError } from '../../api/client'

interface VariantDraft {
  name: string
  price: string
  currency: string
  imageUrl: string
}

export function ProductPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const { productId } = useParams()
  const creating = !productId
  const [categories, setCategories] = useState<Category[]>([])
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [product, setProduct] = useState<Product | null>(null)
  const [variants, setVariants] = useState<Variant[]>([])
  const [drafts, setDrafts] = useState<Record<string, VariantDraft>>({})
  const [variantConflicts, setVariantConflicts] = useState<Record<string, ApiError>>({})
  const [sku, setSku] = useState('')
  const [variantName, setVariantName] = useState('')
  const [price, setPrice] = useState('')
  const [variantCurrency, setVariantCurrency] = useState('USD')
  const [imageUrl, setImageUrl] = useState('')
  const [loading, setLoading] = useState(!creating)
  const [error, setError] = useState<ApiError | null>(null)
  const [conflict, setConflict] = useState<ApiError | null>(null)

  const editable = creating ? canCreateCatalog(permissions) : canUpdateCatalog(permissions)

  function applyProduct(next: Product, nextVariants: Variant[]) {
    setProduct(next)
    setName(next.name)
    setSlug(next.slug)
    setDescription(next.description ?? '')
    setCategoryId(next.category.id)
    setVariants(nextVariants)
    setDrafts(Object.fromEntries(nextVariants.map((variant) => [variant.id, {
      name: variant.name,
      price: String(variant.price),
      currency: variant.currency,
      imageUrl: variant.imageUrl ?? '',
    }])))
    setConflict(null)
    setVariantConflicts({})
  }

  async function reload() {
    if (!productId || !auth.user?.access_token) {
      return
    }
    setLoading(true)
    try {
      const [loaded, variantList] = await Promise.all([
        getProduct(auth.user.access_token, productId),
        listVariants(auth.user.access_token, productId),
      ])
      applyProduct(loaded, variantList)
      setError(null)
    } catch (cause) {
      setError(asApiError(cause, 'Could not load the product'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!canSeeCatalog(permissions) || !auth.user?.access_token) {
      return
    }
    listCategories(auth.user.access_token, 'page=0&size=100&sort=name,asc')
      .then((result) => setCategories(result.items))
      .catch(() => setCategories([]))
  }, [auth.user?.access_token, permissions])

  useEffect(() => {
    if (creating || !canSeeCatalog(permissions)) {
      return
    }
    void reload()
  }, [productId, auth.user?.access_token, permissions])

  if (!canSeeCatalog(permissions)) {
    return <main className="page"><h1>Product</h1><p className="error" role="alert">Catalog access requires catalog read permission.</p></main>
  }
  if (creating && !canCreateCatalog(permissions)) {
    return <main className="page"><h1>New product</h1><p className="error" role="alert">Creating a product requires catalog create permission.</p></main>
  }

  async function saveProduct(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) return
    try {
      if (creating) {
        const created = await createProduct(auth.user.access_token, { name, slug, description, categoryId })
        navigate(`/catalog/products/${created.id}`)
        return
      }
      if (!product) return
      const updated = await updateProduct(auth.user.access_token, product.id, {
        name, slug, description, categoryId, expectedVersion: product.version,
      })
      applyProduct(updated, variants)
      setError(null)
    } catch (cause) {
      const problem = asApiError(cause, 'Could not save the product')
      if (problem.status === 409) {
        setConflict(problem)
        return
      }
      setError(problem)
    }
  }

  async function changeProductActive(active: boolean) {
    if (!product || !auth.user?.access_token) return
    try {
      const updated = await setProductStatus(auth.user.access_token, product.id, active, product.version)
      applyProduct(updated, variants)
      setError(null)
    } catch (cause) {
      const problem = asApiError(cause, 'Could not change activation')
      if (problem.status === 409) setConflict(problem)
      else setError(problem)
    }
  }

  async function addVariant(event: React.FormEvent) {
    event.preventDefault()
    if (!product || !auth.user?.access_token) return
    try {
      await createVariant(auth.user.access_token, product.id, {
        sku, name: variantName, price, currency: variantCurrency, imageUrl,
      })
      setSku('')
      setVariantName('')
      setPrice('')
      setImageUrl('')
      setError(null)
      await reload()
    } catch (cause) {
      setError(asApiError(cause, 'Could not create the variant'))
    }
  }

  function patchDraft(id: string, patch: Partial<VariantDraft>) {
    setDrafts((current) => ({ ...current, [id]: { ...current[id], ...patch } }))
  }

  async function saveVariant(variant: Variant) {
    if (!auth.user?.access_token) return
    const draft = drafts[variant.id]
    try {
      const updated = await updateVariant(auth.user.access_token, variant.id, {
        sku: variant.sku,
        name: draft.name,
        price: draft.price,
        currency: draft.currency,
        imageUrl: draft.imageUrl,
        expectedVersion: variant.version,
      })
      setVariants((current) => current.map((item) => item.id === updated.id ? updated : item))
      patchDraft(updated.id, {
        name: updated.name,
        price: String(updated.price),
        currency: updated.currency,
        imageUrl: updated.imageUrl ?? '',
      })
      setVariantConflicts((current) => {
        const next = { ...current }
        delete next[variant.id]
        return next
      })
    } catch (cause) {
      const problem = asApiError(cause, 'Could not save the variant')
      if (problem.status === 409) {
        setVariantConflicts((current) => ({ ...current, [variant.id]: problem }))
        return
      }
      setError(problem)
    }
  }

  async function changeVariantActive(variant: Variant, active: boolean) {
    if (!auth.user?.access_token) return
    try {
      const updated = await setVariantStatus(auth.user.access_token, variant.id, active, variant.version)
      setVariants((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (cause) {
      const problem = asApiError(cause, 'Could not change the variant')
      if (problem.status === 409) setVariantConflicts((current) => ({ ...current, [variant.id]: problem }))
      else setError(problem)
    }
  }

  return (
    <main className="page">
      <p><Link to="/catalog/products">Products</Link></p>
      <h1>{creating ? 'New product' : name || 'Product'}</h1>
      <CatalogNotice error={error} loading={loading} />
      {conflict ? (
        <div className="banner" role="alert">
          <p>{conflict.message} Your entered values are still in the form.</p>
          {conflict.correlationId ? <p>Reference {conflict.correlationId}</p> : null}
          <button type="button" onClick={() => void reload()}>Reload and review</button>
        </div>
      ) : null}
      <form className="stack" onSubmit={(event) => void saveProduct(event)}>
        <label>Name<input value={name} onChange={(event) => setName(event.target.value)} required disabled={!editable} /></label>
        <label>Slug<input value={slug} onChange={(event) => setSlug(event.target.value)} required disabled={!editable} /></label>
        <label>Description<textarea value={description} onChange={(event) => setDescription(event.target.value)} disabled={!editable} /></label>
        <label>Category
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required disabled={!editable}>
            <option value="">Select a category</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
        </label>
        {product ? <p>{product.active ? 'Active' : 'Inactive'} · version {product.version}</p> : null}
        {editable ? <button type="submit">{creating ? 'Create product' : 'Save product'}</button> : <p>This account can view the product and cannot edit it.</p>}
      </form>
      {product && canActivateCatalog(permissions) ? (
        <button type="button" onClick={() => void changeProductActive(!product.active)}>
          {product.active ? 'Deactivate product' : 'Activate product'}
        </button>
      ) : null}

      {product ? (
        <section>
          <h2>Variants</h2>
          {variants.length === 0 ? <p>No variants yet.</p> : null}
          <ul className="list">
            {variants.map((variant) => {
              const draft = drafts[variant.id]
              const variantConflict = variantConflicts[variant.id]
              return (
                <li key={variant.id}>
                  <form className="stack" onSubmit={(event) => { event.preventDefault(); void saveVariant(variant) }}>
                    <p>{variant.active ? 'Active' : 'Inactive'}</p>
                    <label>SKU<input value={variant.sku} readOnly /></label>
                    <label>Name<input value={draft?.name ?? ''} onChange={(event) => patchDraft(variant.id, { name: event.target.value })} disabled={!canUpdateCatalog(permissions)} /></label>
                    <label>Price<input value={draft?.price ?? ''} onChange={(event) => patchDraft(variant.id, { price: event.target.value })} disabled={!canUpdateCatalog(permissions)} /></label>
                    <label>Currency<input value={draft?.currency ?? ''} maxLength={3} onChange={(event) => patchDraft(variant.id, { currency: event.target.value })} disabled={!canUpdateCatalog(permissions)} /></label>
                    <label>Image URL<input value={draft?.imageUrl ?? ''} onChange={(event) => patchDraft(variant.id, { imageUrl: event.target.value })} disabled={!canUpdateCatalog(permissions)} /></label>
                    {variantConflict ? (
                      <div role="alert">
                        <p>{variantConflict.message} Entered values were kept.</p>
                        <button type="button" onClick={() => void reload()}>Reload and review</button>
                      </div>
                    ) : null}
                    {canUpdateCatalog(permissions) ? <button type="submit">Save variant</button> : null}
                    {canActivateCatalog(permissions) ? (
                      <button type="button" onClick={() => void changeVariantActive(variant, !variant.active)}>
                        {variant.active ? 'Deactivate variant' : 'Activate variant'}
                      </button>
                    ) : null}
                  </form>
                </li>
              )
            })}
          </ul>
          {canCreateCatalog(permissions) ? (
            <form className="stack" onSubmit={(event) => void addVariant(event)}>
              <h3>New variant</h3>
              <label>SKU<input value={sku} onChange={(event) => setSku(event.target.value)} required /></label>
              <label>Name<input value={variantName} onChange={(event) => setVariantName(event.target.value)} required /></label>
              <label>Price<input value={price} onChange={(event) => setPrice(event.target.value)} required /></label>
              <label>Currency<input value={variantCurrency} maxLength={3} onChange={(event) => setVariantCurrency(event.target.value)} required /></label>
              <label>Image URL<input value={imageUrl} onChange={(event) => setImageUrl(event.target.value)} /></label>
              <button type="submit">Create variant</button>
            </form>
          ) : null}
        </section>
      ) : null}
    </main>
  )
}
