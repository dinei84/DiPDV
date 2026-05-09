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

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
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

  if (!res.ok) {
    const error = await safeParseJson(res);
    throw new Error(
      (error as { message?: string })?.message ?? `HTTP ${res.status}`
    );
  }

  return safeParseJson(res);
}
