# Admin Panel Frontend — Design Spec

**Date:** 2026-04-27  
**Scope:** MVP — Login + Dashboard + CRUD Tenants + Module Gating Toggle  
**Stack:** Next.js 16.2.0 + TypeScript + Tailwind (no external UI libs)  
**Port:** 3001

---

## Overview

Single-page admin panel for SUPER_ADMIN users to:
- Manage tenants (list, create, edit)
- Toggle module access per tenant (BASE fixed, PAID interactive)
- View dashboard placeholder

No pagintion, search, or filters in MVP. Desktop-only (no mobile responsiveness).

---

## Architecture

### Directory Structure

```
admin/
├── src/
│   ├── app/
│   │   ├── layout.tsx                    → Root (ToastProvider + Toaster)
│   │   ├── page.tsx                      → Redirects to /login
│   │   ├── login/
│   │   │   └── page.tsx                  → Login form
│   │   └── (admin)/
│   │       ├── layout.tsx                → AdminGuard (Client Component)
│   │       ├── dashboard/page.tsx        → Placeholder
│   │       └── tenants/
│   │           ├── page.tsx              → List tenants
│   │           ├── new/page.tsx          → Create form
│   │           ├── [id]/page.tsx         → Edit + modules
│   │           └── _components/
│   │               ├── TenantForm.tsx    → Reusable form
│   │               ├── ModulesList.tsx   → Modules section
│   │               └── ModuleToggle.tsx  → Individual toggle
│   ├── components/
│   │   ├── AdminGuard.tsx                → Auth check + redirect
│   │   ├── Sidebar.tsx                   → Nav + logout
│   │   ├── Header.tsx                    → User info + role
│   │   └── Toast/
│   │       ├── ToastContext.tsx
│   │       ├── ToastProvider.tsx
│   │       ├── Toaster.tsx
│   │       └── useToast.ts
│   └── lib/
│       ├── api.ts                        → HTTP client + ApiError
│       ├── auth.ts                       → Token/user management
│       ├── types.ts                      → All DTOs
│       └── hooks.ts                      → useTenantModules, etc.
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── .env.local.example
```

---

## Authentication & API

### lib/auth.ts

```typescript
interface AuthData {
  token: string;
  user: {
    id: string;
    email: string;
    role: string;
    tenantId: string;
  };
}

export const getAuth = (): AuthData | null
export const saveAuth = (token: string, user: AuthData['user']): void
export const clearAuth = (): void
export const isAuthenticated = (): boolean
```

**Storage keys:** `dipdv_admin_token`, `dipdv_admin_user` (separate from PDV).

### lib/api.ts

```typescript
class ApiError extends Error {
  constructor(
    public status: number,
    public body: unknown,
    message: string
  ) {
    super(message);
  }
}

export async function apiFetch(
  url: string,
  options?: RequestInit
): Promise<any>
```

**Behavior:**
- Adds `Authorization: Bearer {token}` header
- **401:** `clearAuth()` + SSR-safe redirect
  ```typescript
  if (typeof window !== 'undefined') {
    window.location.href = '/login';
  }
  ```
- **403:** throws `ApiError(403, body, body.message)`
- **Other errors (400/404/422/500):** reads body, throws `ApiError(status, body, body.message ?? "HTTP {status}")`
- All body reads have `.catch(() => null)` fallback

### lib/types.ts

```typescript
export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  enabledModules: string[];  // module codes
}

export interface TenantRequest {
  name: string;
  slug?: string;
  active?: boolean;
}

export interface ModuleCatalogItem {
  code: string;
  name: string;
  description: string;
  tier: 'BASE' | 'PAID';
}

export interface TenantModuleStatus {
  code: string;
  name: string;
  description: string;
  tier: 'BASE' | 'PAID';
  enabled: boolean;
}
```

### lib/hooks.ts

```typescript
export const useTenantModules = (tenantId: string) => {
  // Fetches:
  // GET /api/v1/admin/modules/catalog
  // GET /api/v1/admin/modules/tenants/{tenantId}
  // Returns: { modules: TenantModuleStatus[], loading, error }
}
```

---

## Components

### AdminGuard.tsx

**Client Component** (`'use client'`). Wraps protected routes.

```typescript
export const AdminGuard = ({ children }) => {
  const [isChecking, setIsChecking] = useState(true);
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const auth = getAuth();
    
    if (!auth) {
      router.replace('/login');
      return;
    }
    
    if (auth.user.role !== 'SUPER_ADMIN') {
      clearAuth();
      router.replace('/login?error=forbidden');
      return;
    }
    
    setIsChecking(false);
  }, []);

  if (isChecking) return null; // Blank until verified

  return <>{children}</>;
};
```

**Usage:** Wraps `children` in `(admin)/layout.tsx`.

### Toast System

**ToastProvider** in `app/layout.tsx` (root, not (admin)/layout).

- **Types:** success | error | info
- **Position:** fixed bottom-right
- **Auto-dismiss:** 4s
- **Max toasts:** 3 (4th discards oldest)
- **Deduplication:** same type + message = reset timer, don't add new
- **Manual dismiss:** X button per toast

### Sidebar.tsx

Shared component. Items:
- Dashboard
- Tenants
- Logout (clearAuth + redirect /login)

Header: user name + role (read from localStorage).

### Header.tsx

Shared component. Displays:
- User email/name
- Role
- Tenant ID (for context)

---

## Pages

### /login

**Form:**
- Email + password fields
- Validation inline (required, email format)
- Error message inline above submit button (red, persistent until retry)
- Query param `?error=forbidden` → "Acesso restrito a SUPER_ADMIN"
- Submit: `POST /api/v1/auth/login`
  - Success (200): `saveAuth()` + redirect `/dashboard` (no toast)
  - Error (400/403): display `body.message` inline
- No background distraction — clean form page

### /dashboard

Placeholder. Displays:
- User name
- Tenant ID (SUPER_ADMIN = ffffffff-...)
- Role

### /tenants

**List all tenants:**
- `GET /api/v1/admin/tenants` on load
- **Loading:** spinner/skeleton while pending
- **Error:** inline message in content area
- **Table columns:**
  - Tenant name
  - Slug
  - Status (ativo/inativo toggle state, read-only display)
  - Nº de módulos ativos (from `enabledModules.length`)
  - Actions: "Editar" link to `/tenants/[id]`
- **Button:** "Novo tenant" → `/tenants/new`
- **Row click:** Navigate to `/tenants/[id]`

### /tenants/new

**Create form:**
- Fields: `name` (required), `slug` (optional, auto-filled on blur from name)
- Validation inline
- Submit: `POST /api/v1/admin/tenants`
  - Success (201): toast success "Tenant criado" + redirect `/tenants/{id}` (new tenant's ID from response)
  - Error: toast error with `body.message`

### /tenants/[id]

**Two sections on same page:**

#### Section 1: Tenant Data

- Fields: `name`, `active` (toggle)
- **Loading:** skeleton while `GET /api/v1/admin/tenants/{id}` pending
- Submit button: "Salvar"
- **Salvar:** `PUT /api/v1/admin/tenants/{id}`
  - Success: toast success "Dados salvos"
  - Error: toast error with `body.message`
- No inline feedback on toggle (toggle change is sufficient visual feedback)

#### Section 2: Modules

- **Loading:** skeleton while fetching catalog + tenant modules
- Uses `useTenantModules(tenantId)` hook
- **Grouped by tier:**
  - BASE (fixed, always enabled)
  - PAID (interactive)
- **BASE modules:** toggle disabled, gray, tooltip "Módulo essencial, sempre ativo"
- **PAID modules:** toggle interactive
  - **Click:** immediate UI update (optimistic)
  - **Request in-flight:** individual toggle disabled (isPending)
  - **Success:** no toast (UI already changed)
  - **Error:** revert state + toast error with `body.message`
- Toggle calls:
  - Enable: `POST /api/v1/admin/modules/tenants/{tenantId}/enable` with `{ "code": "..." }`
  - Disable: `POST /api/v1/admin/modules/tenants/{tenantId}/disable` with `{ "code": "..." }`

---

## Feedback Strategy

### Inline (Form Fields)
- Email field in login
- Name, slug in tenant form
- Validation errors below each field

### Inline (Error on Load)
- Spinner on page load
- "Não foi possível carregar tenants. Tente recarregar a página."

### Toast
- **Success:** create tenant, save tenant name
- **Error:** all failed actions (login, create, save, toggle)
- **Types:** success (green ✓), error (red ✕), info (blue ℹ)

### No Toast
- Login success (redirect is confirmation)
- Toggle success (UI feedback is confirmation)

---

## Error Handling

**Backend returns useful error bodies:**
- `{ error: "MODULE_NOT_ENABLED", module: "..." }`
- Form validation: `{ message: "Field X is required" }`
- 403 on restricted operation: `{ message: "Access denied" }`

**Frontend:**
1. Catches `ApiError`
2. Reads `error.body.message` or fallback to `error.message`
3. Displays in toast or inline depending on context

---

## Testing (Out of Scope)

No automated tests in MVP. Manual smoke test only:
1. Navigate without token → redirect /login
2. Login as non-SUPER_ADMIN → "Acesso restrito"
3. Login as SUPER_ADMIN → /dashboard
4. /tenants → list loads
5. Create tenant → appears in list
6. Edit tenant → toggle PAID on/off; BASE disabled
7. Deactivate tenant → toast success

---

## Out of Scope (MVP)

- Pagination, search, filters
- User management within tenant
- Metrics on dashboard
- i18n, dark mode, mobile responsiveness
- Automated test suite
- Error boundary / offline handling

---

## Next Steps After MVP

1. Pagination + search on tenants list
2. User management (list, create, revoke per tenant)
3. Activity log / audit trail
4. Advanced dashboard (usage metrics, billing)
5. Shared UI package (extract api.ts, auth.ts, toast system)
