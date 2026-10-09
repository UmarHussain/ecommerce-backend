import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CartPage } from './CartPage'

const auth = {
  isAuthenticated: true,
  subject: 'customer-a',
  token: 'token-a',
  signinRedirect: vi.fn(),
}

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: auth.isAuthenticated,
    signinRedirect: auth.signinRedirect,
    user: auth.isAuthenticated
      ? { access_token: auth.token, profile: { sub: auth.subject } }
      : null,
  }),
}))

function cartBody(name: string, quantity = 1, version = 2) {
  return {
    version,
    catalogRefresh: 'UNKNOWN',
    checkoutNotice: 'Prices and stock are validated again at checkout.',
    subtotals: [{ currency: 'USD', amount: '79.9900' }],
    items: [{
      catalogVariantId: 'v1',
      sku: 'HEADPHONES-BLK',
      quantity,
      displayName: name,
      imageUrl: null,
      unitPrice: '79.9900',
      currency: 'USD',
      snapshotAt: '2026-10-09T00:00:00Z',
      lineTotal: '79.9900',
      catalogState: 'UNKNOWN',
      currentUnitPrice: null,
    }],
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'corr-cart' },
  })
}

describe('storefront cart', () => {
  beforeEach(() => {
    auth.isAuthenticated = true
    auth.subject = 'customer-a'
    auth.token = 'token-a'
    vi.stubGlobal('fetch', vi.fn())
  })

  it('asks an anonymous visitor to sign in', async () => {
    auth.isAuthenticated = false
    render(<MemoryRouter><CartPage /></MemoryRouter>)
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('keeps a draft on conflict and drops the previous customer after an account switch', async () => {
    vi.mocked(fetch).mockResolvedValue(json(cartBody('Ada headphones')))
    const view = render(<MemoryRouter><CartPage /></MemoryRouter>)
    expect(await screen.findByText('Ada headphones')).toBeInTheDocument()
    expect(screen.getByText(/not rechecked/)).toBeInTheDocument()

    vi.mocked(fetch).mockResolvedValueOnce(json({
      title: 'Conflict',
      status: 409,
      detail: 'stale',
      code: 'CART_STALE_VERSION',
      correlationId: 'corr-cart',
    }, 409))
    fireEvent.change(screen.getByLabelText('Quantity for HEADPHONES-BLK'), { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Update' }))
    expect(await screen.findByText(/draft is still here/)).toBeInTheDocument()
    expect(screen.getByLabelText('Quantity for HEADPHONES-BLK')).toHaveValue('4')
    expect(screen.getByText(/Reference corr-cart/)).toBeInTheDocument()

    auth.subject = 'customer-b'
    auth.token = 'token-b'
    vi.mocked(fetch).mockResolvedValue(json(cartBody('Grace book', 1, 0)))
    view.rerender(<MemoryRouter><CartPage /></MemoryRouter>)
    expect(await screen.findByText('Grace book')).toBeInTheDocument()
    expect(screen.queryByText('Ada headphones')).not.toBeInTheDocument()
    const urls = vi.mocked(fetch).mock.calls.map(([url]) => String(url))
    expect(urls.filter((url) => url.includes('/api/v1/store/cart')).length).toBeGreaterThan(1)
  })
})
