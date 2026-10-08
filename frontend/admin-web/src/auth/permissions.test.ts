import { describe, expect, it } from 'vitest'
import { canActivateCatalog, canAdjustInventory, canCreateCatalog, canManageRoles, canSeeCatalog, canSeeInventory, canSeeUsers, canUpdateCatalog } from './permissions'

describe('permission-aware navigation', () => {
  it('hides user administration without PERM_user.read', () => {
    expect(canSeeUsers(['PERM_catalog.read'])).toBe(false)
    expect(canSeeUsers(['PERM_user.read'])).toBe(true)
  })

  it('splits catalog view, create, edit, and activation', () => {
    expect(canSeeCatalog(['PERM_catalog.read'])).toBe(true)
    expect(canSeeCatalog(['PERM_user.read'])).toBe(false)
    expect(canCreateCatalog(['PERM_catalog.read', 'PERM_catalog.create'])).toBe(true)
    expect(canUpdateCatalog(['PERM_catalog.read', 'PERM_catalog.create'])).toBe(false)
    expect(canActivateCatalog(['PERM_catalog.update'])).toBe(false)
    expect(canActivateCatalog(['PERM_catalog.activate'])).toBe(true)
  })

  it('shows inventory navigation only with inventory read and hides writes without adjust', () => {
    expect(canSeeInventory(['PERM_catalog.read'])).toBe(false)
    expect(canSeeInventory(['PERM_inventory.read'])).toBe(true)
    expect(canAdjustInventory(['PERM_inventory.read'])).toBe(false)
    expect(canAdjustInventory(['PERM_inventory.read', 'PERM_inventory.adjust'])).toBe(true)
  })

  it('hides role bundle creation without PERM_role.manage', () => {
    expect(canManageRoles(['PERM_role.assign', 'PERM_role.read'])).toBe(false)
    expect(canManageRoles(['PERM_role.manage'])).toBe(true)
  })
})
