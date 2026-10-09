import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OrderPage } from './OrderPage'
import { OrdersPage } from './OrdersPage'

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

vi.mock('../api/orders', async () => {
  const actual = await vi.importActual<typeof import('../api/orders')>('../api/orders')
  return { ...actual, ORDER_POLL_MS: 30, ORDER_POLL_LIMIT: 2 }
})

function order(status: string, cancellationRequested = false) {
  return {
    id: 'order-1',
    orderStatus: status,
    paymentStatus: status === 'CONFIRMED' ? 'SUCCEEDED' : status === 'COMPENSATING' ? 'DECLINED' : 'NOT_STARTED',
    fulfilmentStatus: 'NOT_STARTED',
    sagaStep: status === 'CONFIRMED' ? 'COMPLETED' : 'AWAIT_RESERVATION',
    cancellationRequested,
    cleanupStatus: 'NOT_STARTED',
    obligation: status === 'MANUAL_REVIEW' ? 'payment outcome unknown; reservation remains payment-protected' : '',
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

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderOrder() {
  return render(
    <MemoryRouter initialEntries={['/orders/order-1']}>
      <Routes>
        <Route path="/orders/:orderId" element={<OrderPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

describe('storefront order progress', () => {
  beforeEach(() => {
    auth.isAuthenticated = true
    auth.subject = 'customer-a'
    auth.token = 'token-a'
    vi.stubGlobal('fetch', vi.fn())
  })

  it('polls a pending order until it is confirmed and then stops', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(json(order('PENDING_STOCK')))
      .mockResolvedValueOnce(json(order('CONFIRMED')))
    renderOrder()
    expect(await screen.findByText('Waiting for stock to be reserved.')).toBeInTheDocument()
    expect(screen.getByText('Payment is simulated. No card is charged.')).toBeInTheDocument()
    expect(await screen.findByText(/both finished/)).toBeInTheDocument()
    const callsAfterConfirm = vi.mocked(fetch).mock.calls.length
    await delay(120)
    expect(vi.mocked(fetch).mock.calls.length).toBe(callsAfterConfirm)
  })

  it('stops polling when the customer leaves the page', async () => {
    vi.mocked(fetch).mockResolvedValue(json(order('PENDING_PAYMENT')))
    const view = renderOrder()
    expect(await screen.findByText('Waiting for the simulated payment.')).toBeInTheDocument()
    const calls = vi.mocked(fetch).mock.calls.length
    view.unmount()
    await delay(120)
    expect(vi.mocked(fetch).mock.calls.length).toBe(calls)
  })

  it('cancels while the order is still pending and hides cancel after the request is accepted', async () => {
    let cancelled = false
    vi.mocked(fetch).mockImplementation((_url, init) => {
      const method = (init as RequestInit | undefined)?.method ?? 'GET'
      if (method === 'POST') {
        cancelled = true
        return Promise.resolve(json(order('CANCEL_PENDING', true), 202))
      }
      return Promise.resolve(json(cancelled ? order('CANCEL_PENDING', true) : order('PENDING_STOCK')))
    })
    renderOrder()
    expect(await screen.findByRole('button', { name: 'Cancel order' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel order' }))
    expect(await screen.findByText('Cancellation is in progress.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })

  it('stops watching after the poll limit while the order is still pending', async () => {
    vi.mocked(fetch).mockResolvedValue(json(order('PENDING_PAYMENT')))
    renderOrder()
    expect(await screen.findByText('Still in progress. Refresh the page to keep watching.')).toBeInTheDocument()
    expect(screen.getByText('Payment is simulated. No card is charged.')).toBeInTheDocument()
    const calls = vi.mocked(fetch).mock.calls.length
    expect(calls).toBe(2)
    await delay(120)
    expect(vi.mocked(fetch).mock.calls.length).toBe(calls)
  })

  it('stops the previous account poll when the customer changes', async () => {
    vi.mocked(fetch).mockResolvedValue(json(order('PENDING_PAYMENT')))
    const view = renderOrder()
    expect(await screen.findByText('Waiting for the simulated payment.')).toBeInTheDocument()
    auth.subject = 'customer-b'
    auth.token = 'token-b'
    view.rerender(
      <MemoryRouter initialEntries={['/orders/order-1']}>
        <Routes>
          <Route path="/orders/:orderId" element={<OrderPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => {
      expect(authorization(vi.mocked(fetch).mock.calls.at(-1))).toBe('Bearer token-b')
    })
    const switchedAt = vi.mocked(fetch).mock.calls.findIndex((call) => authorization(call) === 'Bearer token-b')
    await delay(120)
    const later = vi.mocked(fetch).mock.calls.slice(switchedAt)
    expect(later.length).toBeGreaterThan(0)
    expect(later.every((call) => authorization(call) === 'Bearer token-b')).toBe(true)
  })

  it.each([
    ['REJECTED', 'Rejected. Nothing was charged.', false],
    ['COMPENSATING', 'Reversing the checkout. This stays pending until each reversal is acknowledged.', true],
    ['MANUAL_REVIEW', 'Needs review. Outstanding stock or payment obligations are still recorded.', true],
    ['CANCELLED', 'Cancelled after the required reversals were acknowledged.', false],
  ])('shows %s progress', async (status, text, cancelVisible) => {
    vi.mocked(fetch).mockResolvedValue(json(order(status)))
    renderOrder()
    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(screen.getByText('Payment is simulated. No card is charged.')).toBeInTheDocument()
    if (status === 'MANUAL_REVIEW') {
      expect(screen.getByText(/Outstanding: payment outcome unknown/)).toBeInTheDocument()
    }
    if (cancelVisible) {
      expect(screen.getByRole('button', { name: 'Cancel order' })).toBeInTheDocument()
    } else {
      expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    }
  })

  it('lists rejection, compensation, review, and cancellation with the simulated payment label', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(json({
      items: [
        order('REJECTED'),
        { ...order('COMPENSATING'), id: 'order-2' },
        { ...order('MANUAL_REVIEW'), id: 'order-3' },
        { ...order('CANCEL_PENDING', true), id: 'order-4' },
      ],
      page: 0,
      size: 20,
      total: 4,
    }))
    render(<MemoryRouter><OrdersPage /></MemoryRouter>)
    expect(await screen.findByText('Rejected. Nothing was charged.')).toBeInTheDocument()
    expect(screen.getByText(/Reversing the checkout/)).toBeInTheDocument()
    expect(screen.getByText(/Needs review/)).toBeInTheDocument()
    expect(screen.getByText('Cancellation is in progress.')).toBeInTheDocument()
    expect(screen.getByText(/payment outcome unknown/)).toBeInTheDocument()
    expect(screen.getAllByText('Payment is simulated.')).toHaveLength(4)
  })

  it('drops the previous customer from the order list when the account changes', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(json({
      items: [order('CONFIRMED')],
      page: 0,
      size: 20,
      total: 1,
    }))
    const view = render(<MemoryRouter><OrdersPage /></MemoryRouter>)
    expect(await screen.findByText(/both finished/)).toBeInTheDocument()
    auth.subject = 'customer-b'
    auth.token = 'token-b'
    vi.mocked(fetch).mockResolvedValueOnce(json({ items: [], page: 0, size: 20, total: 0 }))
    view.rerender(<MemoryRouter><OrdersPage /></MemoryRouter>)
    expect(await screen.findByText('You have no orders yet.')).toBeInTheDocument()
    expect(screen.queryByText(/both finished/)).not.toBeInTheDocument()
  })
})

function authorization(call: unknown[] | undefined) {
  const init = call?.[1] as RequestInit | undefined
  return new Headers(init?.headers).get('Authorization')
}
