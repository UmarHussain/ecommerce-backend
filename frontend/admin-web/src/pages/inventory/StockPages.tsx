import { FormEvent, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { ApiError } from '../../api/client'
import { ProductSummary, Variant, listProducts, listVariants } from '../../api/catalog'
import {
  StockAdjustment,
  StockItem,
  adjustStock,
  getStock,
  listStock,
  newIdempotencyKey,
  setupStock,
  stockHistory,
} from '../../api/inventory'
import { canAdjustInventory, canSeeCatalog, canSeeInventory } from '../../auth/permissions'

const ADJUSTMENT_REASONS = ['INBOUND_RECEIPT', 'CORRECTION', 'DAMAGE_LOSS', 'RETURN']

function messageFor(error: ApiError): string {
  if (error.status === 0) {
    return 'The outcome is uncertain. Retry the same command; do not start a new one.'
  }
  if (error.status === 401) {
    return 'Sign in again. This session was not accepted.'
  }
  if (error.status === 403) {
    return error.code === 'INVENTORY_CATALOG_READ_REQUIRED'
      ? 'Stock setup also requires catalog read permission.'
      : 'You do not have permission for this inventory action.'
  }
  if (error.status === 404) {
    return 'That stock or catalog record was not found.'
  }
  if (error.status === 409) {
    return error.message || 'This command conflicts with the current stock. Review it before trying again.'
  }
  if (error.status === 400) {
    return error.message || 'Check the entered values.'
  }
  if (error.status === 502 || error.status === 503 || error.status === 504) {
    return 'Inventory or catalog is unavailable. Try again shortly.'
  }
  return error.message || 'The inventory request failed.'
}

function Notice({ error, loading = false, empty = false }: { error: ApiError | null; loading?: boolean; empty?: boolean }) {
  if (loading) {
    return <p role="status">Loading inventory…</p>
  }
  if (error) {
    return (
      <div className="error" role="alert">
        <p>{messageFor(error)}</p>
        {error.correlationId ? <p>Reference {error.correlationId}</p> : null}
      </div>
    )
  }
  if (empty) {
    return <p>No matching stock items.</p>
  }
  return null
}

function asApiError(cause: unknown): ApiError {
  return cause instanceof ApiError ? cause : new ApiError(0, 'The request did not finish')
}

export function StockListPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [items, setItems] = useState<StockItem[]>([])
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (!canSeeInventory(permissions) || !auth.user?.access_token) {
      return
    }
    const token = auth.user.access_token
    let cancelled = false
    setLoading(true)
    listStock(token, search, page)
      .then((result) => {
        if (cancelled) return
        setItems(result.items)
        setLast(result.last)
        setError(null)
      })
      .catch((cause: unknown) => {
        if (!cancelled) setError(asApiError(cause))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [permissions, auth.user?.access_token, search, page])

  if (!canSeeInventory(permissions)) {
    return <main className="page"><p className="error" role="alert">Inventory read permission is required.</p></main>
  }

  return (
    <main className="page">
      <p className="eyebrow">Inventory</p>
      <h1>Stock</h1>
      <div className="actions">
        {canAdjustInventory(permissions) ? <Link to="/inventory/new">Set up stock</Link> : null}
      </div>
      <form className="filters" onSubmit={(event) => { event.preventDefault(); setPage(0) }}>
        <label>
          Search SKU or identifier
          <input value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>
      </form>
      <Notice error={error} loading={loading} empty={!loading && items.length === 0} />
      <ul className="list">
        {items.map((item) => (
          <li key={item.id}>
            <Link to={`/inventory/${item.id}`}>{item.sku}</Link>
            <span>Available {item.available}</span>
          </li>
        ))}
      </ul>
      <div className="actions">
        <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</button>
        <button type="button" disabled={last} onClick={() => setPage((current) => current + 1)}>Next</button>
      </div>
    </main>
  )
}

export function StockSetupPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const [query, setQuery] = useState('')
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [variants, setVariants] = useState<Variant[]>([])
  const [variantId, setVariantId] = useState('')
  const [initialOnHand, setInitialOnHand] = useState('0')
  const [note, setNote] = useState('')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [uncertain, setUncertain] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [createdSku, setCreatedSku] = useState<string | null>(null)
  const [command, setCommand] = useState<{ key: string; body: string } | null>(null)

  if (!canAdjustInventory(permissions)) {
    return <main className="page"><p className="error" role="alert">Inventory adjust permission is required to set up stock.</p></main>
  }
  if (!canSeeCatalog(permissions)) {
    return (
      <main className="page">
        <h1>Set up stock</h1>
        <p className="error" role="alert">This account can adjust inventory but does not have catalog read, so it cannot verify a variant. Setup stays unavailable.</p>
      </main>
    )
  }

  async function loadProducts(event: FormEvent) {
    event.preventDefault()
    if (!auth.user?.access_token) return
    try {
      const params = new URLSearchParams({ search: query, page: '0', size: '20' })
      const page = await listProducts(auth.user.access_token, params.toString())
      setProducts(page.items)
      setError(null)
    } catch (cause) {
      setError(asApiError(cause))
    }
  }

  async function chooseProduct(productId: string) {
    if (!auth.user?.access_token) return
    setVariants([])
    setVariantId('')
    try {
      setVariants(await listVariants(auth.user.access_token, productId))
    } catch (cause) {
      setError(asApiError(cause))
    }
  }

  async function submit(reuse: boolean) {
    if (!auth.user?.access_token || submitting) return
    const body = reuse && command
      ? command.body
      : JSON.stringify({
          catalogVariantId: variantId,
          initialOnHand: Number(initialOnHand),
          reasonCode: 'OPENING_BALANCE',
          note,
          reference,
        })
    const key = reuse && command ? command.key : newIdempotencyKey()
    setCommand({ key, body })
    setSubmitting(true)
    setUncertain(false)
    try {
      const result = await setupStock(auth.user.access_token, key, body)
      setCreatedSku(result.stockItem.sku)
      setCommand(null)
      setError(null)
    } catch (cause) {
      const problem = asApiError(cause)
      setError(problem)
      setUncertain(problem.status === 0)
      if (problem.status !== 0) {
        setCommand(null)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page">
      <p className="eyebrow">Inventory</p>
      <h1>Set up stock</h1>
      <p className="muted">The server checks the catalog variant with your token and stores that SKU. A name shown here is only a picker label.</p>
      <form className="filters" onSubmit={(event) => void loadProducts(event)}>
        <label>
          Find a catalog product
          <input value={query} onChange={(event) => setQuery(event.target.value)} />
        </label>
        <button type="submit">Search catalog</button>
      </form>
      <ul className="list">
        {products.map((product) => (
          <li key={product.id}>
            <button type="button" onClick={() => void chooseProduct(product.id)}>{product.name}</button>
            <span>{product.active ? 'Active' : 'Inactive'}</span>
          </li>
        ))}
      </ul>
      <form className="stack" onSubmit={(event) => { event.preventDefault(); void submit(false) }}>
        <label>
          Catalog variant
          <select value={variantId} onChange={(event) => setVariantId(event.target.value)} required>
            <option value="">Select a variant</option>
            {variants.map((variant) => (
              <option key={variant.id} value={variant.id}>{variant.sku} — {variant.name}</option>
            ))}
          </select>
        </label>
        <label>
          Opening on-hand
          <input value={initialOnHand} onChange={(event) => setInitialOnHand(event.target.value)} inputMode="numeric" />
        </label>
        <p>Reason: opening balance. Zero is allowed here and is not an adjustment.</p>
        <label>
          Note
          <input value={note} onChange={(event) => setNote(event.target.value)} />
        </label>
        <label>
          Reference
          <input value={reference} onChange={(event) => setReference(event.target.value)} />
        </label>
        <button type="submit" disabled={submitting || !variantId}>Create stock</button>
      </form>
      <Notice error={error} />
      {uncertain ? <button type="button" onClick={() => void submit(true)}>Retry the same command</button> : null}
      {createdSku ? <p role="status">Stock was created for SKU {createdSku}.</p> : null}
    </main>
  )
}

export function StockDetailPage({ permissions }: { permissions: string[] }) {
  const auth = useAuth()
  const { stockId = '' } = useParams()
  const [stock, setStock] = useState<StockItem | null>(null)
  const [history, setHistory] = useState<StockAdjustment[]>([])
  const [delta, setDelta] = useState('1')
  const [reason, setReason] = useState('INBOUND_RECEIPT')
  const [note, setNote] = useState('')
  const [reference, setReference] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [stale, setStale] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [command, setCommand] = useState<{ key: string; body: string } | null>(null)

  async function load() {
    if (!auth.user?.access_token) return
    setLoading(true)
    try {
      const [item, rows] = await Promise.all([
        getStock(auth.user.access_token, stockId),
        stockHistory(auth.user.access_token, stockId),
      ])
      setStock(item)
      setHistory(rows.items)
      setError(null)
      setStale(false)
    } catch (cause) {
      setError(asApiError(cause))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!canSeeInventory(permissions)) return
    void load()
    // Reload is explicit after a conflict. The stock id and token define the loaded row.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [permissions, auth.user?.access_token, stockId])

  if (!canSeeInventory(permissions)) {
    return <main className="page"><p className="error" role="alert">Inventory read permission is required.</p></main>
  }

  async function submit(reuse: boolean) {
    if (!auth.user?.access_token || !stock || submitting) return
    const body = reuse && command
      ? command.body
      : JSON.stringify({
          delta: Number(delta),
          reasonCode: reason,
          expectedVersion: stock.version,
          note,
          reference,
        })
    const key = reuse && command ? command.key : newIdempotencyKey()
    setCommand({ key, body })
    setSubmitting(true)
    setUncertain(false)
    try {
      const result = await adjustStock(auth.user.access_token, stockId, key, body)
      setStock(result.stockItem)
      setHistory((current) => [result.adjustment, ...current])
      setCommand(null)
      setError(null)
      setStale(false)
    } catch (cause) {
      const problem = asApiError(cause)
      setError(problem)
      setStale(problem.status === 409 && problem.code === 'INVENTORY_STALE_VERSION')
      setUncertain(problem.status === 0)
      if (problem.status !== 0 && problem.code !== 'INVENTORY_STALE_VERSION') {
        setCommand(null)
      }
      if (problem.code === 'INVENTORY_STALE_VERSION') {
        setCommand(null)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page">
      <p className="eyebrow">Inventory</p>
      <h1>{stock?.sku ?? 'Stock'}</h1>
      <p className="muted">
        Catalog was checked when this stock was set up. Deactivating the catalog item does not remove this stock or its history.
        A later receipt or correction records the physical quantity and does not make an inactive item sellable.
      </p>
      <Notice error={error} loading={loading && !stock} />
      {stock ? (
        <dl className="stack">
          <div>On hand {stock.onHand}</div>
          <div>Reserved {stock.reserved}</div>
          <div>Available {stock.available}</div>
          <div>Version {stock.version}</div>
        </dl>
      ) : null}
      {canAdjustInventory(permissions) && stock ? (
        <form className="stack" onSubmit={(event) => { event.preventDefault(); void submit(false) }}>
          <label>
            Delta
            <input aria-label="Delta" value={delta} onChange={(event) => setDelta(event.target.value)} />
          </label>
          <label>
            Reason
            <select aria-label="Reason" value={reason} onChange={(event) => setReason(event.target.value)}>
              {ADJUSTMENT_REASONS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label>
            Note
            <input aria-label="Adjustment note" value={note} onChange={(event) => setNote(event.target.value)} />
          </label>
          <label>
            Reference
            <input aria-label="Adjustment reference" value={reference} onChange={(event) => setReference(event.target.value)} />
          </label>
          <button type="submit" disabled={submitting}>Apply adjustment</button>
        </form>
      ) : null}
      {stale ? <button type="button" onClick={() => void load()}>Reload and review</button> : null}
      {uncertain ? <button type="button" onClick={() => void submit(true)}>Retry the same command</button> : null}
      <h2>History</h2>
      <table>
        <thead>
          <tr>
            <th>Delta</th>
            <th>Before</th>
            <th>After</th>
            <th>Reason</th>
            <th>Actor</th>
            <th>When</th>
          </tr>
        </thead>
        <tbody>
          {history.map((row) => (
            <tr key={row.id}>
              <td>{row.delta}</td>
              <td>{row.beforeOnHand}</td>
              <td>{row.afterOnHand}</td>
              <td>{row.reasonCode}</td>
              <td>{row.actorSubject}</td>
              <td>{row.createdAt}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
