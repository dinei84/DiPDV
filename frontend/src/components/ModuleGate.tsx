'use client';

import { ReactNode } from 'react';
import { useModules } from '@/lib/hooks/useModules';

type Props = {
  module: string;
  fallback?: ReactNode;
  children: ReactNode;
};

export default function ModuleGate({ module, fallback = null, children }: Props) {
  const { has, isLoading } = useModules();

  if (isLoading) return null;

  if (has(module)) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
}
