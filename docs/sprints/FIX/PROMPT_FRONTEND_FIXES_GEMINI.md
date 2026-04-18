# Frontend Fixes — DiPDV (Gemini CLI)

## Contexto mínimo necessário

Projeto Next.js 14 com App Router, TypeScript e Tailwind CSS.
Backend Spring Boot em `http://localhost:8080`.
JWT armazenado em `localStorage` como `dipdv_token`.
Todas as alterações são no diretório `frontend/src/`.

---

## Fix 1 — Hydration mismatch (extensões de browser)

**Arquivo:** `frontend/src/app/layout.tsx`

**Problema:** Extensões do browser (ex: ColorZilla) injetam atributos na tag
`<body>` antes do React iniciar, causando warning de hydration mismatch.

**Alteração:** Adicionar `suppressHydrationWarning` na tag `<body>`.

```diff
- <body className={inter.className}>
+ <body className={inter.className} suppressHydrationWarning>
```

---

## Fix 2 — apiFetch com tratamento de 401

**Arquivo:** `frontend/src/lib/api.ts`

**Problema:** Quando o token expira ou está ausente, `apiFetch` lança uma
exceção genérica. O usuário fica preso na página com erro `Uncaught (in promise)`
sem feedback visual e sem redirect para o login.

**Substituir o conteúdo completo do arquivo por:**

```typescript
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

function redirectToLogin(message?: string) {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('dipdv_token');
  localStorage.removeItem('dipdv_user');
  const msg = message ? `?session=${encodeURIComponent(message)}` : '';
  window.location.href = `/login${msg}`;
}

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  if (typeof window === 'undefined') {
    throw new Error('apiFetch só pode ser chamado no client');
  }

  const token = localStorage.getItem('dipdv_token');

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (res.status === 401) {
    redirectToLogin('Sua sessão expirou. Faça login novamente.');
    throw new Error('Sessão expirada');
  }

  if (res.status === 403) {
    throw new Error('Sem permissão para esta operação');
  }

  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error((error as any).message ?? `Erro HTTP ${res.status}`);
  }

  return res.json() as Promise<T>;
}

export async function apiFetchBlob(
  path: string,
  options?: RequestInit
): Promise<Blob> {
  if (typeof window === 'undefined') {
    throw new Error('apiFetchBlob só pode ser chamado no client');
  }

  const token = localStorage.getItem('dipdv_token');

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (res.status === 401) {
    redirectToLogin('Sua sessão expirou. Faça login novamente.');
    throw new Error('Sessão expirada');
  }

  if (!res.ok) throw new Error(`Erro HTTP ${res.status}`);
  return res.blob();
}
```

---

## Fix 3 — Root page (remove boilerplate Next.js)

**Arquivo:** `frontend/src/app/page.tsx`

**Problema:** A raiz `/` ainda exibe a página padrão do Next.js.
Deve redirecionar para o PDV se autenticado, ou para `/login` se não.

**Substituir o conteúdo completo do arquivo por:**

```typescript
'use client';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { isAuthenticated } from '@/lib/auth';

export default function RootPage() {
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) {
      router.replace('/pdv');
    } else {
      router.replace('/login');
    }
  }, [router]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent
                      rounded-full animate-spin" />
    </div>
  );
}
```

---

## Fix 4 — AuthGuard (route guard client-side)

**Arquivo:** `frontend/src/components/AuthGuard.tsx` *(criar)*

**Problema:** Rotas protegidas (PDV, relatórios) são acessíveis sem token.
Como o token está em `localStorage` (client-side), a proteção precisa ser
via componente, não via `middleware.ts`.

**Criar o arquivo com o conteúdo:**

```typescript
'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { isAuthenticated } from '@/lib/auth';

interface Props {
  children: React.ReactNode;
}

export default function AuthGuard({ children }: Props) {
  const router = useRouter();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace('/login');
    } else {
      setChecked(true);
    }
  }, [router]);

  if (!checked) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent
                        rounded-full animate-spin" />
      </div>
    );
  }

  return <>{children}</>;
}
```

**Usar no layout do PDV** — `frontend/src/app/(pdv)/layout.tsx`:

Localizar o componente de layout e envolver o conteúdo com `<AuthGuard>`:

```typescript
import AuthGuard from '@/components/AuthGuard';

export default function PdvLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      {/* conteúdo existente do layout — header, nav, DashboardWidget etc */}
      {children}
    </AuthGuard>
  );
}
```

---

## Fix 5 — SSR-safe date no reports page

**Arquivo:** `frontend/src/app/(pdv)/reports/page.tsx`

**Problema:** `const today = new Date().toISOString().split('T')[0]` roda
no servidor e no cliente. Em horários próximos à virada do dia pode gerar
datas diferentes entre SSR e hydration, causando mismatch.

**Localizar as linhas:**
```typescript
const today = new Date().toISOString().split('T')[0];
const [from, setFrom] = useState(today);
const [to, setTo] = useState(today);
```

**Substituir por:**
```typescript
const [from, setFrom] = useState('');
const [to, setTo] = useState('');

useEffect(() => {
  const today = new Date().toISOString().split('T')[0];
  setFrom(today);
  setTo(today);
}, []);
```

---

## Fix 6 — Mensagem de sessão expirada na tela de login

**Arquivo:** `frontend/src/app/(auth)/login/page.tsx`

**Problema:** O `apiFetch` redireciona para `/login?session=...` quando o
token expira, mas a tela de login não exibe a mensagem.

**Localizar** a função do componente de login e adicionar logo após os
`useState` existentes:

```typescript
import { useSearchParams } from 'next/navigation';

// dentro do componente, após os useState:
const searchParams = useSearchParams();
const sessionMessage = searchParams.get('session');
```

**Localizar** o bloco de erro existente:
```typescript
{error && (
  <div className="bg-red-50 text-red-700 p-3 rounded mb-4 text-sm">
    {error}
  </div>
)}
```

**Adicionar antes dele:**
```typescript
{sessionMessage && (
  <div className="bg-amber-50 text-amber-700 p-3 rounded mb-4 text-sm">
    {sessionMessage}
  </div>
)}
```

> O componente que usa `useSearchParams` precisa estar dentro de um
> `<Suspense>`. Envolver o componente de login em `<Suspense>` no
> arquivo de página ou no layout se o Next.js reclamar em build.

---

## Verificação após as alterações

```bash
cd frontend

# Build sem erros
npm run build

# Checar se não há erros de TypeScript
npx tsc --noEmit
```

Esperado: `build success`, 0 erros TypeScript.

Testar em runtime:
```bash
npm run dev
```

1. Acessar `http://localhost:3000` sem estar logado → deve redirecionar para `/login`
2. Acessar `http://localhost:3000/reports` sem estar logado → deve redirecionar para `/login`
3. Fazer login → deve redirecionar para `/pdv` ou `/`
4. Expirar token manualmente (`localStorage.removeItem('dipdv_token')`)
   e chamar um endpoint → deve redirecionar para `/login?session=...`
   com a mensagem em amarelo

---

## O que NÃO alterar

- `lib/auth.ts` — não modificar
- `components/dashboard/` — não modificar
- `components/reports/` — não modificar
- Qualquer arquivo de backend — fora do escopo
- `middleware.ts` — não criar (localStorage não é acessível no Edge Runtime)
