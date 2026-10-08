import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CatalogPage } from './CatalogPage'
import { ProductPage } from './ProductPage'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'corr-store' },
  })
}

describe('storefront catalog', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('loads products anonymously and shows an empty filter result', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/categories')) return jsonResponse([])
      return jsonResponse({ items: [], page: 0, last: true })
    })
    render(<MemoryRouter><CatalogPage /></MemoryRouter>)
    expect(await screen.findByText(/No active products match/)).toBeInTheDocument()
    const calls = vi.mocked(fetch).mock.calls
    expect(calls.some(([url]) => String(url).includes('/api/v1/store/catalog/products'))).toBe(true)
    for (const [, init] of calls) {
      const headers = new Headers(init?.headers)
      expect(headers.get('Authorization')).toBeNull()
    }
  })

  it('shows a selected active variant and a not-found state', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({
      id: '20000000-0000-0000-0000-000000000001',
      name: 'Headphones',
      slug: 'headphones',
      description: 'Over ear',
      active: true,
      category: { id: 'c1', name: 'Electronics', slug: 'electronics', active: true },
      variants: [
        { id: 'v1', sku: 'HEAD-BLK', name: 'Black', price: '79.9900', currency: 'USD', imageUrl: 'https://example.test/black.jpg', active: true },
        { id: 'v2', sku: 'HEAD-WHT', name: 'White', price: '81.0000', currency: 'USD', imageUrl: null, active: true },
      ],
    }))
    const { unmount } = render(
      <MemoryRouter initialEntries={['/catalog/20000000-0000-0000-0000-000000000001']}>
        <Routes><Route path="/catalog/:productId" element={<ProductPage />} /></Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'Headphones' })).toBeInTheDocument()
    expect(screen.getByText('79.9900 USD')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('radio', { name: /White/ }))
    expect(screen.getByText('81.0000 USD')).toBeInTheDocument()
    unmount()

    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ detail: 'missing', code: 'CATALOG_RESOURCE_NOT_FOUND', correlationId: 'corr-store' }, 404))
    render(
      <MemoryRouter initialEntries={['/catalog/missing']}>
        <Routes><Route path="/catalog/:productId" element={<ProductPage />} /></Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/not available/)).toBeInTheDocument()
    expect(screen.getByText(/Reference corr-store/)).toBeInTheDocument()
  })

  it('shows an unavailable catalog', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ detail: 'down', code: 'GATEWAY_DOWNSTREAM_UNAVAILABLE', correlationId: 'corr-store' }, 503))
    render(<MemoryRouter><CatalogPage /></MemoryRouter>)
    expect(await screen.findByText(/Catalog is unavailable/)).toBeInTheDocument()
  })
})
