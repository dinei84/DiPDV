export interface AuthData {
  token: string;
  userId: string;
  tenantId: string;
  name: string;
  role: 'ADMIN' | 'MANAGER' | 'CASHIER';
  expiresIn: number;
}

export function saveAuth(data: AuthData) {
  localStorage.setItem('dipdv_token', data.token);
  localStorage.setItem('dipdv_user', JSON.stringify(data));
}

export function getAuth(): AuthData | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('dipdv_user');
  return raw ? (JSON.parse(raw) as AuthData) : null;
}

export function clearAuth() {
  localStorage.removeItem('dipdv_token');
  localStorage.removeItem('dipdv_user');
}

export function isAuthenticated(): boolean {
  if (typeof window === 'undefined') return false;
  return !!localStorage.getItem('dipdv_token');
}
