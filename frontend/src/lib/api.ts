const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

function redirectToLogin(message?: string) {
  if (typeof window === 'undefined') return;

  localStorage.removeItem('dipdv_token');
  localStorage.removeItem('dipdv_user');

  const query = message ? `?session=${encodeURIComponent(message)}` : '';
  window.location.href = `/login${query}`;
}

function getToken() {
  if (typeof window === 'undefined') return null;

  return localStorage.getItem('dipdv_token');
}

async function readErrorMessage(res: Response) {
  const error = await res.json().catch(() => ({}));

  return (
    (error as { message?: string }).message ?? `Erro HTTP ${res.status}`
  );
}

function assertClientUsage(methodName: 'apiFetch' | 'apiFetchBlob') {
  if (typeof window === 'undefined') {
    throw new Error(`${methodName} só pode ser chamado no client`);
  }
}

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  assertClientUsage('apiFetch');

  const token = getToken();

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
    throw new Error(await readErrorMessage(res));
  }

  return res.json() as Promise<T>;
}

export async function apiFetchBlob(
  path: string,
  options?: RequestInit
): Promise<Blob> {
  assertClientUsage('apiFetchBlob');

  const token = getToken();

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

  if (!res.ok) {
    throw new Error(`Erro HTTP ${res.status}`);
  }

  return res.blob();
}
