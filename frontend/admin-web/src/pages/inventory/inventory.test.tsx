import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StockDetailPage, StockListPage, StockSetupPage } from './StockPages'

const authState = { token: 'staff-token' }

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { access_token: authState.token },
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
  }),
}))

const stock = {
  id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  catalogVariantId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  sku: 'HEADPHONES-BLK',
  onHand: 5,
  reserved: 2,
  available: 3,
  version: 4,
  productNameSnapshot: 'Headphones',
  variantNameSnapshot: 'Black',
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'corr-stock' },
  })
}

describe('admin inventory screens', () => {
  beforeEach(() => {
    authState.token = 'staff-token'
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal('crypto', { randomUUID: () => 'key-from-test' })
  })

  it('hides stock without inventory read', () => {
    render(<MemoryRouter><StockListPage permissions={['PERM_catalog.read']} /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent(/inventory read permission/i)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows available stock and hides adjustment controls from a reader', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/adjustments')) {
        return jsonResponse({ items: [], last: true })
      }
      return jsonResponse(stock)
    })
    render(
      <MemoryRouter initialEntries={['/inventory/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa']}>
        <Routes>
          <Route path="/inventory/:stockId" element={<StockDetailPage permissions={['PERM_inventory.read']} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Available 3')).toBeInTheDocument()
    expect(screen.getByText('Reserved 2')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Apply adjustment' })).not.toBeInTheDocument()
  })

  it('explains that setup needs catalog read', () => {
    render(<MemoryRouter><StockSetupPage permissions={['PERM_inventory.adjust']} /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent(/does not have catalog read/i)
    expect(screen.queryByRole('button', { name: 'Create stock' })).not.toBeInTheDocument()
  })

  it('keeps the adjustment draft when the version is stale', async () => {
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if ((init?.method ?? 'GET') === 'POST') {
        return jsonResponse({
          detail: 'Stock changed since it was loaded',
          code: 'INVENTORY_STALE_VERSION',
          correlationId: 'corr-stock',
        }, 409)
      }
      if (url.includes('/adjustments')) {
        return jsonResponse({
          items: [{
            id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
            delta: 1,
            beforeOnHand: 4,
            afterOnHand: 5,
            reasonCode: 'INBOUND_RECEIPT',
            actorSubject: 'subject-1',
            createdAt: '2026-10-08T12:00:00Z',
          }],
          last: true,
        })
      }
      return jsonResponse(stock)
    })
    render(
      <MemoryRouter initialEntries={['/inventory/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa']}>
        <Routes>
          <Route path="/inventory/:stockId" element={<StockDetailPage permissions={['PERM_inventory.read', 'PERM_inventory.adjust']} />} />
        </Routes>
      </MemoryRouter>,
    )
    const delta = await screen.findByLabelText('Delta')
    fireEvent.change(delta, { target: { value: '9' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply adjustment' }))
    expect(await screen.findByRole('button', { name: 'Reload and review' })).toBeInTheDocument()
    expect(delta).toHaveValue('9')
    expect(screen.getByText(/Reference corr-stock/)).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'INBOUND_RECEIPT' })).toBeInTheDocument()
  })

  it('retries an uncertain adjustment with the same idempotency key', async () => {
    const keys: string[] = []
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if ((init?.method ?? 'GET') === 'POST') {
        keys.push(new Headers(init?.headers).get('Idempotency-Key') ?? '')
        if (keys.length === 1) {
          throw new TypeError('network down')
        }
        return jsonResponse({
          stockItem: { ...stock, onHand: 6, available: 4, version: 5 },
          adjustment: {
            id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
            delta: 1,
            beforeOnHand: 5,
            afterOnHand: 6,
            reasonCode: 'INBOUND_RECEIPT',
            actorSubject: 'subject-1',
            createdAt: '2026-10-08T12:01:00Z',
          },
        })
      }
      if (url.includes('/adjustments')) {
        return jsonResponse({ items: [], last: true })
      }
      return jsonResponse(stock)
    })
    render(
      <MemoryRouter initialEntries={['/inventory/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa']}>
        <Routes>
          <Route path="/inventory/:stockId" element={<StockDetailPage permissions={['PERM_inventory.read', 'PERM_inventory.adjust']} />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByLabelText('Delta')
    fireEvent.click(screen.getByRole('button', { name: 'Apply adjustment' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Retry the same command' }))
    await waitFor(() => expect(keys).toEqual(['key-from-test', 'key-from-test']))
    expect(await screen.findByText('Available 4')).toBeInTheDocument()
  })
})
