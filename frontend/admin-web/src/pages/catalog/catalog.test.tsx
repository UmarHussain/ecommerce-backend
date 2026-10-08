import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CategoriesPage } from './CategoriesPage'
import { CategoryPage } from './CategoryPage'
import { ProductPage } from './ProductPage'

const authState = { token: 'staff-token' }

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { access_token: authState.token },
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
  }),
}))

const category = {
  id: '10000000-0000-0000-0000-000000000001',
  name: 'Electronics',
  slug: 'electronics',
  active: false,
  version: 2,
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'corr-test' },
  })
}

describe('admin catalog screens', () => {
  beforeEach(() => {
    authState.token = 'staff-token'
    vi.stubGlobal('fetch', vi.fn())
  })

  it('hides catalog administration without read permission', () => {
    render(<MemoryRouter><CategoriesPage permissions={[]} /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent(/catalog read permission/i)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows inactive categories and hides create for a viewer', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({
      items: [category], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true, sort: 'name,asc',
    }))
    render(<MemoryRouter><CategoriesPage permissions={['PERM_catalog.read']} /></MemoryRouter>)
    const link = await screen.findByRole('link', { name: 'Electronics' })
    expect(link.closest('li')).toHaveTextContent('Inactive')
    expect(screen.queryByRole('link', { name: 'New category' })).not.toBeInTheDocument()
  })

  it('lets a creator open the create form and keeps an editor from creating', async () => {
    const creator = render(<MemoryRouter><CategoryPage permissions={['PERM_catalog.read', 'PERM_catalog.create']} /></MemoryRouter>)
    expect(screen.getByRole('button', { name: 'Create category' })).toBeInTheDocument()
    creator.unmount()

    render(<MemoryRouter><CategoryPage permissions={['PERM_catalog.read', 'PERM_catalog.update', 'PERM_catalog.activate']} /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent(/catalog create permission/i)
  })

  it('keeps entered edits when a save is stale and offers reload', async () => {
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (method === 'GET' && url.includes('/categories/')) {
        return jsonResponse(category)
      }
      if (method === 'PUT') {
        return jsonResponse({ detail: 'Catalog data changed since it was loaded', code: 'CATALOG_STALE_VERSION', correlationId: 'corr-test' }, 409)
      }
      return jsonResponse({})
    })
    render(
      <MemoryRouter initialEntries={['/catalog/categories/10000000-0000-0000-0000-000000000001']}>
        <Routes>
          <Route path="/catalog/categories/:categoryId" element={<CategoryPage permissions={['PERM_catalog.read', 'PERM_catalog.update']} />} />
        </Routes>
      </MemoryRouter>,
    )
    const name = await screen.findByLabelText('Name')
    await screen.findByDisplayValue('Electronics')
    fireEvent.change(name, { target: { value: 'Draft name' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save category' }))
    expect(await screen.findByRole('button', { name: 'Reload and review' })).toBeInTheDocument()
    expect(name).toHaveValue('Draft name')
    expect(screen.getByText(/Reference corr-test/)).toBeInTheDocument()
  })

  it('shows variant sku as read only and hides edit controls from a viewer', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/categories?')) {
        return jsonResponse({ items: [category], page: 0, last: true })
      }
      if (url.endsWith('/variants')) {
        return jsonResponse([{
          id: '30000000-0000-0000-0000-000000000001',
          productId: '20000000-0000-0000-0000-000000000001',
          sku: 'HEADPHONES-BLK',
          name: 'Black',
          price: '79.9900',
          currency: 'USD',
          imageUrl: 'https://example.test/black.jpg',
          active: true,
          version: 1,
        }])
      }
      return jsonResponse({
        id: '20000000-0000-0000-0000-000000000001',
        name: 'Headphones',
        slug: 'headphones',
        description: 'Over ear',
        active: true,
        version: 4,
        category,
        variants: [],
      })
    })
    render(
      <MemoryRouter initialEntries={['/catalog/products/20000000-0000-0000-0000-000000000001']}>
        <Routes>
          <Route path="/catalog/products/:productId" element={<ProductPage permissions={['PERM_catalog.read']} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByDisplayValue('HEADPHONES-BLK')).toHaveAttribute('readonly')
    expect(screen.queryByRole('button', { name: 'Save variant' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create variant' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save product' })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/cannot edit it/)).toBeInTheDocument())
  })
})
