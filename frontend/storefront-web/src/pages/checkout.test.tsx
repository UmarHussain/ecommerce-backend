import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CHECKOUT_ATTEMPT_STORAGE } from '../api/orders'
import { CheckoutPage } from './CheckoutPage'

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

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function cart() {
  return {
    version: 3,
    catalogRefresh: 'FRESH',
    checkoutNotice: 'Prices and stock are validated again at checkout.',
    subtotals: [{ currency: 'USD', amount: '20.00' }],
    items: [{
      catalogVariantId: 'v1',
      sku: 'MUG-WHT',
      quantity: 2,
      displayName: 'White mug',
      imageUrl: null,
      unitPrice: '10.00',
      currency: 'USD',
      snapshotAt: '2026-10-09T00:00:00Z',
      lineTotal: '20.00',
      catalogState: 'CONFIRMED',
      currentUnitPrice: '10.00',
    }],
  }
}

function profile() {
  return {
    addresses: [{
      id: 'addr-1',
      label: 'Home',
      line1: '1 Main',
      line2: null,
      city: 'Austin',
      region: null,
      postalCode: '78701',
      countryCode: 'US',
    }],
  }
}

function quote() {
  return {
    id: 'quote-1',
    cartVersion: 3,
    currency: 'USD',
    merchandiseTotal: '20.00',
    shippingTotal: '0.00',
    taxTotal: '0.00',
    grandTotal: 20,
    shippingPolicy: 'LOCAL_DEMO_FREE_SHIPPING',
    taxPolicy: 'LOCAL_DEMO_TAX_NOT_CALCULATED',
    expiresAt: '2026-10-09T12:10:00Z',
    address: {
      addressId: 'addr-1',
      label: 'Home',
      line1: '1 Main',
      line2: null,
      city: 'Austin',
      region: null,
      postalCode: '78701',
      countryCode: 'US',
    },
    lines: [{
      catalogVariantId: 'v1',
      sku: 'MUG-WHT',
      displayName: 'White mug',
      quantity: 2,
      unitPrice: '10.00',
      lineTotal: '20.00',
    }],
  }
}

describe('storefront checkout', () => {
  beforeEach(() => {
    auth.isAuthenticated = true
    auth.subject = 'customer-a'
    auth.token = 'token-a'
    sessionStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('asks an anonymous visitor to sign in', async () => {
    auth.isAuthenticated = false
    render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows the quoted amount and reuses the checkout key after an uncertain response', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
    render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    expect(await screen.findByText(/White mug/)).toBeInTheDocument()

    vi.mocked(fetch).mockResolvedValueOnce(json(quote()))
    fireEvent.click(screen.getByRole('button', { name: 'Review quote' }))
    const review = await screen.findByRole('region', { name: 'Quote' })
    expect(within(review).getByText('Amount 20.00 USD')).toBeInTheDocument()
    expect(within(review).getByText('Expires 2026-10-09T12:10:00Z')).toBeInTheDocument()
    expect(within(review).getByText(/LOCAL_DEMO_FREE_SHIPPING/)).toBeInTheDocument()
    expect(within(review).getByText('Home: 1 Main, Austin 78701 US')).toBeInTheDocument()

    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('network'))
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByText(/same checkout key/)).toBeInTheDocument()

    vi.mocked(fetch).mockResolvedValueOnce(json({
      id: 'order-1',
      orderStatus: 'PENDING_STOCK',
      paymentStatus: 'NOT_STARTED',
      fulfilmentStatus: 'NOT_STARTED',
      sagaStep: 'AWAIT_RESERVATION',
      cancellationRequested: false,
      cleanupStatus: 'NOT_STARTED',
      obligation: '',
      currency: 'USD',
      merchandiseTotal: '20.00',
      shippingTotal: '0.00',
      taxTotal: '0.00',
      grandTotal: '20.00',
      shippingPolicy: 'LOCAL_DEMO_FREE_SHIPPING',
      taxPolicy: 'LOCAL_DEMO_TAX_NOT_CALCULATED',
      paymentSimulated: true,
      quoteId: 'quote-1',
      cartVersion: 3,
      createdAt: '2026-10-09T12:00:00Z',
      updatedAt: '2026-10-09T12:00:00Z',
      address: quote().address,
      lines: quote().lines,
    }, 202))
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))

    await waitFor(() => {
      const acceptCalls = vi.mocked(fetch).mock.calls.filter(([, init]) => {
        const headers = new Headers((init as RequestInit | undefined)?.headers)
        return headers.has('Idempotency-Key')
      })
      expect(acceptCalls).toHaveLength(2)
      const first = new Headers((acceptCalls[0][1] as RequestInit).headers).get('Idempotency-Key')
      const second = new Headers((acceptCalls[1][1] as RequestInit).headers).get('Idempotency-Key')
      expect(first).toBeTruthy()
      expect(second).toBe(first)
    })
  })

  it('requires a new quote when the reviewed total is no longer valid', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
      .mockResolvedValueOnce(json(quote()))
    render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    expect(await screen.findByRole('button', { name: 'Review quote' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: 'Review quote' }))
    expect(await screen.findByRole('button', { name: 'Place order' })).toBeInTheDocument()

    vi.mocked(fetch).mockResolvedValueOnce(json({
      detail: 'The cart changed after the quote was reviewed',
      code: 'ORDER_REVIEW_REQUIRED',
      reason: 'CART_CHANGED',
    }, 409))
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByText(/Request a new quote/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Place order' })).not.toBeInTheDocument()
    expect(sessionStorage.getItem(CHECKOUT_ATTEMPT_STORAGE)).toBeNull()
  })

  it('restores the same quote and checkout key after the page is opened again', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
      .mockResolvedValueOnce(json(quote()))
    const view = render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Review quote' }))
    expect(await screen.findByRole('region', { name: 'Quote' })).toBeInTheDocument()
    const key = storedKey()

    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('network'))
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByRole('link', { name: 'Open Orders' })).toBeInTheDocument()
    view.unmount()

    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
    render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    expect(await screen.findByRole('region', { name: 'Quote' })).toBeInTheDocument()
    expect(storedKey()).toBe(key)

    vi.mocked(fetch).mockResolvedValueOnce(json({ id: 'order-1' }, 202))
    fireEvent.click(screen.getByRole('button', { name: 'Place order' }))
    await waitFor(() => {
      const headers = new Headers((vi.mocked(fetch).mock.calls.at(-1)?.[1] as RequestInit).headers)
      expect(headers.get('Idempotency-Key')).toBe(key)
    })
  })

  it('drops the quote and checkout key when the account changes', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
      .mockResolvedValueOnce(json(quote()))
    const view = render(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Review quote' }))
    expect(await screen.findByRole('region', { name: 'Quote' })).toBeInTheDocument()
    expect(storedKey()).toBeTruthy()

    auth.subject = 'customer-b'
    auth.token = 'token-b'
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(cart()))
      .mockResolvedValueOnce(json(profile()))
    view.rerender(<MemoryRouter><CheckoutPage /></MemoryRouter>)
    await waitFor(() => {
      expect(screen.queryByRole('region', { name: 'Quote' })).not.toBeInTheDocument()
    })
    expect(sessionStorage.getItem(CHECKOUT_ATTEMPT_STORAGE)).toBeNull()
    expect(await screen.findByRole('button', { name: 'Review quote' })).toBeInTheDocument()
  })
})

function storedKey() {
  const raw = sessionStorage.getItem(CHECKOUT_ATTEMPT_STORAGE)
  if (!raw) return null
  return (JSON.parse(raw) as { key?: string }).key ?? null
}
