'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { getAuth, clearAuth } from '@/lib/auth';

interface AuthGuardProps {
  children: React.ReactNode;
}

export default function AuthGuard({ children }: AuthGuardProps) {
  const [isChecking, setIsChecking] = useState(true);
  const router = useRouter();
  const searchParams = useSearchParams();
  const error = searchParams.get('error');

  useEffect(() => {
    const auth = getAuth();

    if (!auth) {
      router.replace('/login');
      return;
    }

    const validRoles = ['ADMIN', 'MANAGER', 'CASHIER'];
    if (!validRoles.includes(auth.role)) {
      clearAuth();
      router.replace('/login');
      return;
    }

    setIsChecking(false);
  }, [router]);

  if (isChecking) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-gray-400 animate-pulse font-medium">
          Verificando acesso...
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
