import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getProduct, type Product, type Variant } from '../api/catalog'
import { ApiError } from '../api/client'

export function ProductPage() {
  const { productId } = useParams()
  const [product, setProduct] = useState<Product | null>(null)
  const [selected, setSelected] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

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
        </>
      ) : null}
    </main>
  )
}
