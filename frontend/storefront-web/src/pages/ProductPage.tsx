import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useParams } from 'react-router-dom'
import { getCart, setQuantity } from '../api/cart'
import { getProduct, type Product, type Variant } from '../api/catalog'
import { ApiError } from '../api/client'

export function ProductPage() {
  const auth = useAuth()
  const { productId } = useParams()
  const [product, setProduct] = useState<Product | null>(null)
  const [selected, setSelected] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (!productId) return
    setLoading(true)
    getProduct(productId)
      .then((loaded) => {
        const active = loaded.variants.filter((variant) => variant.active)
        setProduct({ ...loaded, variants: active })
        setSelected(active[0]?.id ?? '')
        setError(null)
      })
      .catch((cause: unknown) => {
        setProduct(null)
        setError(cause instanceof ApiError ? cause : new ApiError(0, 'Could not load the product'))
      })
      .finally(() => setLoading(false))
  }, [productId])

  const variant: Variant | undefined = product?.variants.find((item) => item.id === selected)

  async function addToCart(chosen: Variant) {
    const token = auth.user?.access_token
    if (!token || pending) return
    setPending(true)
    setNotice(null)
    try {
      const current = await getCart(token)
      const existing = current.items.find((item) => item.sku === chosen.sku)
      const quantity = (existing?.quantity ?? 0) + 1
      if (quantity > 99) {
        setNotice('This cart already has 99 of that item.')
        return
      }
      await setQuantity(token, chosen.sku, quantity, current.version)
      setNotice('Added to your cart. Checkout will check the price and stock again.')
    } catch (cause: unknown) {
      if (!(cause instanceof ApiError) || cause.status === 0) {
        setNotice('The cart could not be confirmed. Open your cart to review it. Nothing was added twice.')
        return
      }
      if (cause.status === 409) {
        setNotice('The cart changed. Open your cart and review it before trying again.')
        return
      }
      setNotice(cause.status === 503 || cause.status === 504
        ? 'Catalog is unavailable, so the item was not added.'
        : cause.message)
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="page">
      <p><Link to="/catalog">Catalog</Link></p>
      {loading ? <p role="status">Loading product…</p> : null}
      {error ? (
        <div className="error" role="alert">
          <p>{error.status === 404 ? 'This product is not available.' : error.status === 502 || error.status === 503 || error.status === 504 ? 'Catalog is unavailable.' : error.message}</p>
          {error.correlationId ? <p>Reference {error.correlationId}</p> : null}
        </div>
      ) : null}
      {product ? (
        <>
          <h1>{product.name}</h1>
          <p>{product.description}</p>
          <p>{product.category.name}</p>
          {product.variants.length === 0 ? <p>No active variants are available.</p> : (
            <fieldset>
              <legend>Variant</legend>
              {product.variants.map((item) => (
                <label key={item.id}>
                  <input
                    type="radio"
                    name="variant"
                    value={item.id}
                    checked={selected === item.id}
                    onChange={() => setSelected(item.id)}
                  />
                  {item.name} · {item.price} {item.currency}
                </label>
              ))}
            </fieldset>
          )}
          {variant?.imageUrl ? <img src={variant.imageUrl} alt="" /> : null}
          {variant ? <p>{variant.price} {variant.currency}</p> : null}
          {notice ? <p role="status">{notice}</p> : null}
          {variant && !auth.isAuthenticated ? (
            <button type="button" onClick={() => void auth.signinRedirect()}>Sign in to add to cart</button>
          ) : null}
          {variant && auth.isAuthenticated ? (
            <button type="button" disabled={pending} onClick={() => void addToCart(variant)}>
              Add to cart
            </button>
          ) : null}
        </>
      ) : null}
    </main>
  )
}
