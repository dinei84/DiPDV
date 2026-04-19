# Prompt 3 — Sprint 4: Admin Frontend + Cookie Auth

---

## Contexto

Backend admin com 7 endpoints validados. Este prompt implementa:
1. Endpoint de login admin com cookie HttpOnly no backend
2. App Next.js admin do zero no monorepo

**Branch backend:** `feature/US-SA01-super-admin-infra` (continuar)
**Branch admin frontend:** criar `feature/US-SA03-admin-frontend` a partir de `develop`

---

## Parte A — Backend: Login Admin com Cookie HttpOnly

### Por que um endpoint separado?

O login normal (`POST /api/v1/auth/login`) retorna o JWT no body.
O login admin precisa setar um cookie HttpOnly — navegador nunca expõe
o valor via JavaScript, eliminando XSS como vetor de ataque.

### A.1 — Novo endpoint no AdminController

Adicionar ao `AdminController.java`:

```java
/**
 * Login exclusivo do SUPER_ADMIN.
 * Retorna o JWT via cookie HttpOnly em vez de body.
 * O cookie é lido pelo middleware.ts do app admin.
 */
@PostMapping("/auth/login")
@Operation(summary = "Login do SUPER_ADMIN via cookie seguro")
public ResponseEntity<AdminLoginResponse> adminLogin(
        @Valid @RequestBody AdminLoginRequest request,
        HttpServletResponse response) {

    AuthResponse auth = authService.login(
        new LoginRequest(
            MasterTenantConstants.MASTER_TENANT_ID,
            request.email(),
            request.password()
        )
    );

    // Validar que o usuário é realmente SUPER_ADMIN
    if (auth.role() != UserRole.SUPER_ADMIN) {
        throw new BusinessException(
            "Acesso restrito ao painel administrativo",
            HttpStatus.FORBIDDEN);
    }

    // Setar cookie HttpOnly — JavaScript nunca acessa este valor
    ResponseCookie cookie = ResponseCookie.from("dipdv_admin_token", auth.token())
        .httpOnly(true)
        .secure(true)           // apenas HTTPS em produção
        .path("/")
        .maxAge(Duration.ofHours(8))
        .sameSite("Strict")
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return ResponseEntity.ok(new AdminLoginResponse(
        auth.userId(),
        auth.name(),
        auth.role().name()
        // token NÃO é retornado no body
    ));
}

/**
 * Logout: limpa o cookie.
 */
@PostMapping("/auth/logout")
public ResponseEntity<Void> adminLogout(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from("dipdv_admin_token", "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(Duration.ZERO)  // expirar imediatamente
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return ResponseEntity.noContent().build();
}
```

**DTOs novos:**

```java
// AdminLoginRequest.java
public record AdminLoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}

// AdminLoginResponse.java — sem token
public record AdminLoginResponse(
    UUID userId,
    String name,
    String role
) {}
```

### A.2 — JwtAuthFilter: ler cookie além do header

Localizar `JwtAuthFilter.java`, método `extractToken`.
Substituir por:

```java
private String extractToken(HttpServletRequest request) {
    // 1. Tentar header Authorization (PDV e API)
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.substring(7);
    }

    // 2. Tentar cookie admin (painel admin)
    if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("dipdv_admin_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }

    return null;
}
```

### A.3 — SecurityConfig: liberar endpoints admin/auth

Adicionar nas rotas públicas do `SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/logout").authenticated()
```

### A.4 — CORS: permitir credenciais do admin app

No `SecurityConfig.java`, método `corsConfigurationSource()`, adicionar:

```java
config.setAllowedOrigins(List.of(
    "http://localhost:3000",    // PDV dev
    "http://localhost:3001",    // Admin dev ← novo
    "https://admin.dipdv.app"  // Admin prod ← novo
));
config.setAllowCredentials(true); // já existe, confirmar
```

### A.5 — Testar endpoints antes do frontend

```bash
# Login admin — deve setar cookie na resposta
curl -s -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \        # salvar cookies
  -d '{
    "email": "superadmin@dipdv.app",
    "password": "SuperAdmin@2025!"
  }' | jq .

# Usar o cookie salvo para acessar endpoint protegido
curl -s "http://localhost:8080/api/v1/admin/tenants" \
  -b cookies.txt | jq .  # usar cookies salvos
```

Esperado: login retorna `{"userId":"...","name":"Super Admin DiPDV","role":"SUPER_ADMIN"}` sem token.
Segundo request usa o cookie e retorna a lista de tenants.

---

## Parte B — Frontend Admin (Gemini CLI / Codex CLI)

### B.1 — Scaffold do projeto

```bash
# Na raiz do monorepo DiPDV/
npx create-next-app@latest admin \
  --typescript \
  --tailwind \
  --eslint \
  --app \
  --src-dir \
  --import-alias "@/*" \
  --no-experimental-app

cd admin
```

Criar `admin/.env.local`:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_APP_ENV=development
```

### B.2 — middleware.ts (proteção SSR via cookie)

**Arquivo:** `admin/src/middleware.ts`

```typescript
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const PUBLIC_PATHS = ['/login'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Permitir rotas públicas
  if (PUBLIC_PATHS.some(p => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // Verificar cookie do admin
  const adminToken = request.cookies.get('dipdv_admin_token')?.value;

  if (!adminToken) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('from', pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|api).*)',
  ],
};
```

### B.3 — lib/api.ts

```typescript
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export async function adminFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    credentials: 'include',         // envia cookie automaticamente
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (res.status === 401 || res.status === 403) {
    if (typeof window !== 'undefined') {
      window.location.href = '/login?session=expired';
    }
    throw new Error('Sessão expirada');
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as any).message ?? `Erro HTTP ${res.status}`);
  }

  return res.json() as Promise<T>;
}

export async function adminLogin(email: string, password: string) {
  const res = await fetch(`${API_URL}/api/v1/admin/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as any).message ?? 'Credenciais inválidas');
  }

  return res.json();
}

export async function adminLogout() {
  await fetch(`${API_URL}/api/v1/admin/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  });
  window.location.href = '/login';
}
```

### B.4 — Tela de Login

**Arquivo:** `admin/src/app/login/page.tsx`

```typescript
'use client';
import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { adminLogin } from '@/lib/api';
import { Suspense } from 'react';

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const sessionMsg = params.get('session');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await adminLogin(email, password);
      router.push('/dashboard');
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-950">
      <div className="bg-slate-900 border border-slate-800 p-8 rounded-xl
                      shadow-2xl w-full max-w-md">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-white">DiPDV</h1>
          <p className="text-slate-400 text-sm mt-1">Painel Administrativo</p>
        </div>

        {sessionMsg === 'expired' && (
          <div className="bg-amber-950 border border-amber-800 text-amber-300
                          p-3 rounded-lg mb-4 text-sm">
            Sessão expirada. Faça login novamente.
          </div>
        )}

        {error && (
          <div className="bg-red-950 border border-red-800 text-red-300
                          p-3 rounded-lg mb-4 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-slate-400 mb-1">E-mail</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="w-full bg-slate-800 border border-slate-700 text-white
                         rounded-lg px-3 py-2 text-sm focus:outline-none
                         focus:border-violet-500"
              required
            />
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">Senha</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="w-full bg-slate-800 border border-slate-700 text-white
                         rounded-lg px-3 py-2 text-sm focus:outline-none
                         focus:border-violet-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-violet-600 hover:bg-violet-500 disabled:opacity-50
                       text-white rounded-lg py-2 text-sm font-medium transition"
          >
            {loading ? 'Entrando...' : 'Entrar no Painel'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
```

### B.5 — Layout do Dashboard

**Arquivo:** `admin/src/app/layout.tsx`

```typescript
import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'DiPDV Admin',
  description: 'Painel administrativo DiPDV',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <body className={`${inter.className} bg-slate-950 text-white`}
            suppressHydrationWarning>
        {children}
      </body>
    </html>
  );
}
```

**Arquivo:** `admin/src/app/dashboard/layout.tsx`

```typescript
'use client';
import { adminLogout } from '@/lib/api';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Visão Geral' },
  { href: '/dashboard/tenants', label: 'Clientes' },
  { href: '/dashboard/engagement', label: 'Engajamento' },
];

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-56 bg-slate-900 border-r border-slate-800
                        flex flex-col">
        <div className="p-6 border-b border-slate-800">
          <p className="text-white font-bold">DiPDV</p>
          <p className="text-slate-500 text-xs mt-0.5">Admin</p>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {NAV_ITEMS.map(item => (
            <Link
              key={item.href}
              href={item.href}
              className={`block px-3 py-2 rounded-lg text-sm transition ${
                pathname === item.href
                  ? 'bg-violet-600 text-white'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="p-4 border-t border-slate-800">
          <button
            onClick={adminLogout}
            className="w-full text-left text-sm text-slate-500
                       hover:text-red-400 transition px-3 py-2"
          >
            Sair
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        {children}
      </main>
    </div>
  );
}
```

### B.6 — Dashboard: Visão Geral

**Arquivo:** `admin/src/app/dashboard/page.tsx`

```typescript
'use client';
import { useEffect, useState } from 'react';
import { adminFetch } from '@/lib/api';

interface GlobalStats {
  tenantCount: number;
  activeTenantCount: number;
  totalOrders: number;
  totalRevenue: number;
  topTenants: { id: string; name: string; orders30d: number; revenue30d: number }[];
}

export default function DashboardPage() {
  const [stats, setStats] = useState<GlobalStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminFetch<GlobalStats>('/api/v1/admin/dashboard/stats')
      .then(setStats)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-6 h-6 border-2 border-violet-500 border-t-transparent
                      rounded-full animate-spin" />
    </div>
  );

  if (error) return (
    <div className="p-8 text-red-400 text-sm">{error}</div>
  );

  return (
    <div className="p-8">
      <h1 className="text-xl font-bold text-white mb-6">Visão Geral</h1>

      {/* Cards de métricas */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total de clientes', value: stats?.tenantCount ?? 0 },
          { label: 'Clientes ativos', value: stats?.activeTenantCount ?? 0 },
          { label: 'Pedidos totais', value: stats?.totalOrders ?? 0 },
          { label: 'Faturamento total',
            value: `R$ ${(stats?.totalRevenue ?? 0).toFixed(2)}` },
        ].map(card => (
          <div key={card.label}
               className="bg-slate-900 border border-slate-800
                          rounded-xl p-4">
            <p className="text-slate-500 text-xs uppercase tracking-wide">
              {card.label}
            </p>
            <p className="text-2xl font-bold text-white mt-1">{card.value}</p>
          </div>
        ))}
      </div>

      {/* Top tenants */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h2 className="text-sm font-medium text-slate-300 mb-4">
          Top clientes — últimos 30 dias
        </h2>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-slate-500 text-left">
              <th className="pb-3 font-normal">Cliente</th>
              <th className="pb-3 font-normal text-right">Pedidos</th>
              <th className="pb-3 font-normal text-right">Faturamento</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {stats?.topTenants.map(t => (
              <tr key={t.id}>
                <td className="py-3 text-white">{t.name}</td>
                <td className="py-3 text-right text-slate-300">{t.orders30d}</td>
                <td className="py-3 text-right text-slate-300">
                  R$ {t.revenue30d.toFixed(2)}
                </td>
              </tr>
            ))}
            {!stats?.topTenants.length && (
              <tr>
                <td colSpan={3} className="py-6 text-center text-slate-600">
                  Nenhum dado no período
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

### B.7 — Dashboard: Lista de Clientes

**Arquivo:** `admin/src/app/dashboard/tenants/page.tsx`

```typescript
'use client';
import { useEffect, useState } from 'react';
import { adminFetch } from '@/lib/api';
import Link from 'next/link';

interface Tenant {
  id: string;
  name: string;
  slug: string | null;
  planType: string;
  active: boolean;
  lastActivityAt: string | null;
  userCount: number;
}

const PLAN_COLORS: Record<string, string> = {
  TRIAL:     'bg-amber-900 text-amber-300',
  ACTIVE:    'bg-emerald-900 text-emerald-300',
  SUSPENDED: 'bg-red-900 text-red-300',
};

export default function TenantsPage() {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminFetch<Tenant[]>('/api/v1/admin/tenants')
      .then(setTenants)
      .finally(() => setLoading(false));
  }, []);

  function formatActivity(at: string | null) {
    if (!at) return 'Nunca';
    const days = Math.floor(
      (Date.now() - new Date(at).getTime()) / 86_400_000
    );
    if (days === 0) return 'Hoje';
    if (days === 1) return 'Ontem';
    return `${days} dias atrás`;
  }

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-6 h-6 border-2 border-violet-500 border-t-transparent
                      rounded-full animate-spin" />
    </div>
  );

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-white">Clientes</h1>
        <Link href="/dashboard/tenants/new"
              className="bg-violet-600 hover:bg-violet-500 text-white
                         rounded-lg px-4 py-2 text-sm transition">
          + Novo cliente
        </Link>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-800 text-slate-500 text-left">
              <th className="p-4 font-normal">Cliente</th>
              <th className="p-4 font-normal">Plano</th>
              <th className="p-4 font-normal">Usuários</th>
              <th className="p-4 font-normal">Última atividade</th>
              <th className="p-4 font-normal">Status</th>
              <th className="p-4 font-normal"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {tenants.map(t => (
              <tr key={t.id} className="hover:bg-slate-800/50 transition">
                <td className="p-4">
                  <p className="text-white font-medium">{t.name}</p>
                  {t.slug && (
                    <p className="text-slate-500 text-xs">{t.slug}</p>
                  )}
                </td>
                <td className="p-4">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium
                                   ${PLAN_COLORS[t.planType] ?? ''}`}>
                    {t.planType}
                  </span>
                </td>
                <td className="p-4 text-slate-300">{t.userCount}</td>
                <td className="p-4 text-slate-400">
                  {formatActivity(t.lastActivityAt)}
                </td>
                <td className="p-4">
                  <span className={`text-xs ${
                    t.active ? 'text-emerald-400' : 'text-red-400'
                  }`}>
                    {t.active ? 'Ativo' : 'Inativo'}
                  </span>
                </td>
                <td className="p-4">
                  <Link href={`/dashboard/tenants/${t.id}`}
                        className="text-violet-400 hover:text-violet-300
                                   text-xs transition">
                    Detalhes →
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

### B.8 — Dashboard: Engajamento

**Arquivo:** `admin/src/app/dashboard/engagement/page.tsx`

```typescript
'use client';
import { useEffect, useState } from 'react';
import { adminFetch } from '@/lib/api';

interface Metrics {
  id: string;
  name: string;
  planType: string;
  orders30d: number;
  revenue30d: number;
  engagementStatus: 'ACTIVE' | 'AT_RISK' | 'INACTIVE' | 'NEVER';
  lastActivityAt: string | null;
}

const STATUS_CONFIG = {
  ACTIVE:   { label: 'Ativo',    color: 'text-emerald-400', dot: 'bg-emerald-400' },
  AT_RISK:  { label: 'Em risco', color: 'text-amber-400',   dot: 'bg-amber-400'   },
  INACTIVE: { label: 'Inativo',  color: 'text-red-400',     dot: 'bg-red-400'     },
  NEVER:    { label: 'Nunca usou', color: 'text-slate-500', dot: 'bg-slate-600'   },
};

export default function EngagementPage() {
  const [metrics, setMetrics] = useState<Metrics[]>([]);
  const [filter, setFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminFetch<Metrics[]>('/api/v1/admin/dashboard/engagement')
      .then(setMetrics)
      .finally(() => setLoading(false));
  }, []);

  const filtered = filter === 'ALL'
    ? metrics
    : metrics.filter(m => m.engagementStatus === filter);

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-6 h-6 border-2 border-violet-500 border-t-transparent
                      rounded-full animate-spin" />
    </div>
  );

  return (
    <div className="p-8">
      <h1 className="text-xl font-bold text-white mb-2">Engajamento</h1>
      <p className="text-slate-500 text-sm mb-6">
        Atividade dos clientes nos últimos 30 dias
      </p>

      {/* Filtros */}
      <div className="flex gap-2 mb-6">
        {['ALL', 'ACTIVE', 'AT_RISK', 'INACTIVE', 'NEVER'].map(s => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-3 py-1.5 rounded-lg text-xs transition ${
              filter === s
                ? 'bg-violet-600 text-white'
                : 'bg-slate-800 text-slate-400 hover:text-white'
            }`}
          >
            {s === 'ALL' ? 'Todos' : STATUS_CONFIG[s as keyof typeof STATUS_CONFIG]?.label ?? s}
            {' '}
            <span className="opacity-60">
              ({s === 'ALL' ? metrics.length : metrics.filter(m => m.engagementStatus === s).length})
            </span>
          </button>
        ))}
      </div>

      <div className="space-y-3">
        {filtered.map(m => {
          const cfg = STATUS_CONFIG[m.engagementStatus];
          return (
            <div key={m.id}
                 className="bg-slate-900 border border-slate-800 rounded-xl
                            p-4 flex items-center gap-4">
              <div className={`w-2 h-2 rounded-full flex-shrink-0 ${cfg.dot}`} />
              <div className="flex-1 min-w-0">
                <p className="text-white text-sm font-medium truncate">{m.name}</p>
                <p className="text-slate-500 text-xs">{m.planType}</p>
              </div>
              <div className="text-right flex-shrink-0">
                <p className="text-slate-300 text-sm">{m.orders30d} pedidos</p>
                <p className="text-slate-500 text-xs">
                  R$ {m.revenue30d.toFixed(2)}
                </p>
              </div>
              <span className={`text-xs flex-shrink-0 ${cfg.color}`}>
                {cfg.label}
              </span>
            </div>
          );
        })}
        {filtered.length === 0 && (
          <p className="text-center text-slate-600 py-12 text-sm">
            Nenhum cliente nesta categoria
          </p>
        )}
      </div>
    </div>
  );
}
```

### B.9 — Root page redirect

**Arquivo:** `admin/src/app/page.tsx`

```typescript
import { redirect } from 'next/navigation';
export default function RootPage() {
  redirect('/dashboard');
}
```

---

## Parte C — Validação

### C.1 — Backend

```bash
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

# Testar login com cookie
curl -s -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -c admin-cookies.txt \
  -d '{"email":"superadmin@dipdv.app","password":"SuperAdmin@2025!"}' | jq .
```
Esperado: `{"userId":"...","name":"Super Admin DiPDV","role":"SUPER_ADMIN"}`

```bash
# Usar cookie para acessar endpoint protegido
curl -s http://localhost:8080/api/v1/admin/tenants \
  -b admin-cookies.txt | jq 'length'
```
Esperado: número >= 1.

```bash
# ADMIN normal sem cookie → 403
curl -s http://localhost:8080/api/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .status
```
Esperado: `403`.

### C.2 — Frontend

```bash
cd admin
npm install
npm run dev -- --port 3001
```

Verificar em `http://localhost:3001`:
- `http://localhost:3001/` redireciona para `/dashboard`
- `/dashboard` sem cookie → redireciona para `/login` (middleware.ts)
- Login com `superadmin@dipdv.app` → redireciona para `/dashboard`
- Dashboard exibe cards de métricas globais
- `/dashboard/tenants` lista clientes com badges de plano
- `/dashboard/engagement` exibe status com filtros
- Botão "Sair" limpa o cookie e volta para `/login`

---

## Parte D — Commits

```bash
# Backend — adicionar endpoints de auth admin
cd backend
git add src/main/java/com/dipdv/modules/admin/controller/AdminController.java
git add src/main/java/com/dipdv/modules/admin/dto/AdminLoginRequest.java
git add src/main/java/com/dipdv/modules/admin/dto/AdminLoginResponse.java
git add src/main/java/com/dipdv/shared/security/JwtAuthFilter.java
git add src/main/java/com/dipdv/shared/security/SecurityConfig.java

git commit -m "feat(admin): login admin via cookie HttpOnly + CORS admin app

- POST /api/v1/admin/auth/login: seta cookie dipdv_admin_token HttpOnly
- POST /api/v1/admin/auth/logout: limpa cookie
- JwtAuthFilter: lê token de cookie além do header Authorization
- SecurityConfig: CORS permite localhost:3001 e admin.dipdv.app
- Bloqueia login com cookie se role != SUPER_ADMIN"

git push origin feature/US-SA01-super-admin-infra

# Frontend admin — branch separada
git checkout develop
git pull origin develop
git checkout -b feature/US-SA03-admin-frontend

cd admin
git add .
git commit -m "feat(admin-frontend): app Next.js painel administrativo DiPDV

- Scaffold Next.js 14 com TypeScript + Tailwind (dark theme)
- middleware.ts: protege rotas via cookie HttpOnly (Edge Runtime)
- adminFetch: client HTTP com credentials include (sem Bearer header)
- Login: cookie HttpOnly setado pelo backend, sem token no JS
- Dashboard: métricas globais, top tenants
- /tenants: lista de clientes com plano, atividade e status
- /engagement: health check com filtros ACTIVE|AT_RISK|INACTIVE|NEVER"

git push origin feature/US-SA03-admin-frontend
```

---

## Checklist final

- [ ] Cookie `dipdv_admin_token` setado no login via curl
- [ ] Endpoint `/admin/tenants` acessível com cookie, bloqueado sem
- [ ] ADMIN normal com Bearer → 403 nos endpoints admin
- [ ] Frontend: `http://localhost:3001` sem cookie → `/login`
- [ ] Login com SUPER_ADMIN funcional no browser
- [ ] Dashboard carrega métricas globais
- [ ] `/tenants` lista clientes com badges coloridos
- [ ] `/engagement` exibe status com filtros funcionando
- [ ] Logout limpa cookie e redireciona
- [ ] 2 PRs abertos: backend e admin-frontend

---

## O que NÃO implementar aqui

- Página de detalhe do tenant (`/tenants/[id]`) — pós-MVP
- Criar novo tenant via frontend (`/tenants/new`) — pós-MVP
- Suspender tenant via frontend — pós-MVP
- Gráficos com Chart.js no admin — pós-MVP
- Deploy no Railway — próximo passo após validação
