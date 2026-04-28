import { clearAuth, getToken } from './auth';

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  constructor(
    public status: number,
    public body: unknown,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function safeParseJson(text: string) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const token = getToken();

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  // Handle 401 Unauthorized - clear auth and redirect to login
  if (res.status === 401) {
    clearAuth();
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
    throw new ApiError(res.status, null, 'Unauthorized');
  }

  // Common body extraction logic for error and success
  const text = await res.text();
  const body = safeParseJson(text);

  // Handle 403 Forbidden - throw error without logout
  if (res.status === 403) {
    const message = (body as { message?: string } | null)?.message ?? 'Forbidden';
    throw new ApiError(res.status, body, message);
  }

  // Handle other errors
  if (!res.ok) {
    const message = (body as { message?: string } | null)?.message ?? `HTTP ${res.status}`;
    throw new ApiError(res.status, body, message);
  }

  return body as T;
}

export async function apiGet<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'GET' });
}

export async function apiPost<T>(path: string, data?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
  });
}

export async function apiPut<T>(path: string, data?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PUT',
    body: data ? JSON.stringify(data) : undefined,
  });
}
