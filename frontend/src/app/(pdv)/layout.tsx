'use client';

import DashboardWidget from '@/components/dashboard/DashboardWidget';
import AuthGuard from '@/components/AuthGuard';
import { ModulesProvider } from '@/lib/hooks/useModules';
import ModuleGate from '@/components/ModuleGate';
import { clearAuth } from '@/lib/auth';
import { useRouter } from 'next/navigation';
import { Suspense } from 'react';

export default function PdvLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();

  const handleLogout = () => {
    clearAuth();
    router.replace('/login');
  };

  return (
    <Suspense fallback={null}>
      <AuthGuard>
        <ModulesProvider>
          <div className="min-h-screen bg-gray-100 flex flex-col">
            <header className="bg-blue-900 text-white px-6 py-3 flex items-center justify-between shadow-md">
              <div className="flex items-center gap-8">
                <span className="font-bold text-xl tracking-tight">DiPDV</span>
                <nav className="flex gap-6 text-sm font-medium">
                  <a href="/" className="hover:text-blue-200 transition">
                    PDV
                  </a>
                  <ModuleGate module="REPORTS">
                    <a href="/reports" className="hover:text-blue-200 transition">
                      Relatórios
                    </a>
                  </ModuleGate>
                </nav>
              </div>
              <button
                onClick={handleLogout}
                className="text-sm bg-blue-800 hover:bg-blue-700 px-3 py-1.5 rounded-md transition"
              >
                Sair
              </button>
            </header>
            <main className="p-4 flex-1">
              <ModuleGate module="REPORTS">
                <DashboardWidget />
              </ModuleGate>
              {children}
            </main>
          </div>
        </ModulesProvider>
      </AuthGuard>
    </Suspense>
  );
}
