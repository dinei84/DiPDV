const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface ApiErrorEnvelope {
  message?: string;
  fields?: Array<{
    field: string;
    message: string;
  }>;
}

export interface AdminLoginPayload {
  userId: string;
  name: string;
  role: string;
}

function buildHeaders(options?: RequestInit): HeadersInit {
  const hasFormData = typeof FormData !== "undefined" && options?.body instanceof FormData;
  const headers = new Headers(options?.headers);

  headers.set("Accept", "application/json");

  if (!hasFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return headers;
}

async function readErrorMessage(response: Response, fallback: string) {
  const error = (await response.json().catch(() => null)) as ApiErrorEnvelope | null;

  if (error?.fields?.length) {
    return error.fields.map((field) => `${field.field}: ${field.message}`).join(" | ");
  }

  return error?.message ?? fallback;
}

export async function adminFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    cache: "no-store",
    credentials: "include",
    headers: buildHeaders(options),
  });

  if (response.status === 401 || response.status === 403) {
    if (typeof window !== "undefined") {
      const from = `${window.location.pathname}${window.location.search}`;
      const loginUrl = new URL("/login", window.location.origin);
      loginUrl.searchParams.set("session", "expired");
      loginUrl.searchParams.set("from", from);
      window.location.assign(loginUrl.toString());
    }

    throw new Error("Sessao expirada");
  }

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `Erro HTTP ${response.status}`));
  }

  if (response.status === 204) {
    return null as T;
  }

  return (await response.json()) as T;
}

export async function adminLogin(email: string, password: string) {
  const response = await fetch(`${API_URL}/api/v1/admin/auth/login`, {
    method: "POST",
    cache: "no-store",
    credentials: "include",
    headers: buildHeaders(),
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, "Credenciais invalidas"));
  }

  return (await response.json()) as AdminLoginPayload;
}

export async function adminLogout() {
  const response = await fetch(`${API_URL}/api/v1/admin/auth/logout`, {
    method: "POST",
    cache: "no-store",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response, "Nao foi possivel encerrar a sessao"));
  }
}
