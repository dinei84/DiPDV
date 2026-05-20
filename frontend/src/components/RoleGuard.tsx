'use client';

import { ReactNode } from 'react';
import { getAuth } from '@/lib/auth';
import { Role } from '@/lib/types';

interface RoleGuardProps {
  children: ReactNode;
  allowedRoles: Role[];
  fallback?: ReactNode;
}

/**
 * Componente para proteção de partes da UI baseada na role do usuário.
 */
export default function RoleGuard({ children, allowedRoles, fallback = null }: RoleGuardProps) {
  const auth = getAuth();
  
  if (!auth || !allowedRoles.includes(auth.role)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
