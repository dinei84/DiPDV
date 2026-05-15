import { clearAuth } from './auth';
import { ApiError } from './api-error';
import { toast } from './toast';

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

async function safeParseJson(response: Response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

type ApiFetchOptions = RequestInit & {
  suppressErrorToast?: boolean;
};

export async function apiFetch<T>(
  path: string,
  options?: ApiFetchOptions
): Promise<T> {
  const token =
    typeof window !== 'undefined' ? localStorage.getItem('dipdv_token') : null;

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (res.status === 401) {
    clearAuth();
    toast.error('Sessão expirada. Faça login novamente.');
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
    throw new ApiError(res.status, null, 'Unauthorized');
  }

  const body = await safeParseJson(res);

  if (res.status === 403) {
    const message = body?.message || body?.module || 'Acesso negado';
    if (!options?.suppressErrorToast) toast.error(message);
    throw new ApiError(res.status, body, message);
  }

  if (!res.ok) {
    const message = body?.message || `Erro ${res.status}`;
    if (!options?.suppressErrorToast) toast.error(message);
    throw new ApiError(res.status, body, message);
  }

  return body as T;
}

export async function apiGet<T>(path: string, options?: ApiFetchOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'GET' });
}

export async function apiPost<T>(path: string, data?: any): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function apiPut<T>(path: string, data?: any): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function apiDelete<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'DELETE' });
}

export async function apiPatch<T>(path: string, data?: any): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PATCH',
    body: JSON.stringify(data),
  });
}
