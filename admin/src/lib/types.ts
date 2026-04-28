// Tenant DTOs
export interface TenantRequest {
  name: string;
  slug: string;
  active?: boolean;
}

export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  enabledModules: string[];
}

// Module DTOs
export interface ModuleCatalogItem {
  code: string;
  name: string;
  description: string;
  tier: 'BASE' | 'PAID';
  createdAt: string;
}

export interface TenantModuleStatus {
  moduleCode: string;
  enabled: boolean;
  enabledAt: string;
  enabledBy?: string;
}

// Auth DTOs
export interface AuthData {
  token: string;
  userId: string;
  tenantId: string;
  name: string;
  role: 'ADMIN' | 'MANAGER' | 'CASHIER' | 'SUPER_ADMIN';
  expiresIn: number;
}

// UI DTOs
export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  duration?: number;
}
