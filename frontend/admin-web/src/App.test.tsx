import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HomePage } from './pages/Home'

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
  }),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    Link: ({ children, to }: { children: string; to: string }) => <a href={to}>{children}</a>,
  }
})

describe('HomePage', () => {
  it('renders the administration shell and a sign-in action', () => {
    render(<HomePage permissions={[]} denied={false} />)
    expect(screen.getByRole('heading', { name: 'Administration' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('explains access denied for customer-only staff login', () => {
    render(<HomePage permissions={[]} denied={true} />)
    expect(screen.getByText(/no staff privileges/)).toBeInTheDocument()
  })
})
