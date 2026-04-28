'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getAuth, clearAuth } from '@/lib/auth';

interface AdminGuardProps {
  children: React.ReactNode;
}

export default function AdminGuard({ children }: AdminGuardProps) {
  const [isChecking, setIsChecking] = useState(true);
  const router = useRouter();

  useEffect(() => {
    const auth = getAuth();

    // No auth: redirect to login
    if (!auth) {
      router.replace('/login');
      return;
    }

    // Auth exists but not SUPER_ADMIN: logout and redirect with error
    if (auth.role !== 'SUPER_ADMIN') {
      clearAuth();
      router.replace('/login?error=forbidden');
      return;
    }

    // All checks passed
    setIsChecking(false);
  }, [router]);

  if (isChecking) return null;

  return <>{children}</>;
}
