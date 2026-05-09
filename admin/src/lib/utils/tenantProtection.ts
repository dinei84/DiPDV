export function getProtectedTenantReason(tenantId: string): string | null {
  if (tenantId === '00000000-0000-0000-0000-000000000001') {
    return 'Tenant default não pode ser desativado';
  }
  if (tenantId === 'ffffffff-ffff-ffff-ffff-ffffffffffff') {
    return 'Tenant master de sistema não pode ser desativado';
  }
  return null;
}
