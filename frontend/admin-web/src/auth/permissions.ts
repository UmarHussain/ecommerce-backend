export function hasPermission(permissions: string[] | undefined, name: string): boolean {
  return Boolean(permissions?.includes(name))
}

export function canSeeUsers(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_user.read')
}

export function canSeeRoles(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_role.read')
}

export function canManageRoles(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_role.manage')
}

export function canAssignRoles(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_role.assign')
}

export function canCreateUsers(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_user.create')
}

export function canSeeCatalog(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_catalog.read')
}

export function canCreateCatalog(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_catalog.create')
}

export function canUpdateCatalog(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_catalog.update')
}

export function canActivateCatalog(permissions: string[] | undefined): boolean {
  return hasPermission(permissions, 'PERM_catalog.activate')
}
