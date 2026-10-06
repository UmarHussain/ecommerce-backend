import { describe, expect, it } from 'vitest'
import { canManageRoles, canSeeUsers } from './permissions'

describe('permission-aware navigation', () => {
  it('hides user administration without PERM_user.read', () => {
    expect(canSeeUsers(['PERM_catalog.read'])).toBe(false)
    expect(canSeeUsers(['PERM_user.read'])).toBe(true)
  })

  it('hides role bundle creation without PERM_role.manage', () => {
    expect(canManageRoles(['PERM_role.assign', 'PERM_role.read'])).toBe(false)
    expect(canManageRoles(['PERM_role.manage'])).toBe(true)
  })
})
