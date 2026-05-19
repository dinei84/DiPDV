'use client';

import DashboardWidget from '@/components/dashboard/DashboardWidget';
import AuthGuard from '@/components/AuthGuard';
import CashRegisterIndicator from '@/components/CashRegister/CashRegisterIndicator';
import OpenOrdersIndicator from '@/components/Orders/OpenOrdersIndicator';
import { ModulesProvider } from '@/lib/hooks/useModules';
import { CashRegisterProvider } from '@/lib/cash-register/CashRegisterContext';
import { OrdersProvider } from '@/lib/orders/OrdersContext';
import ModuleGate from '@/components/ModuleGate';
import { clearAuth, getAuth } from '@/lib/auth';
import { useRouter } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';
import { ChevronDown, Settings } from 'lucide-react';
import Link from 'next/link';

export default function PdvLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const auth = useMemo(() => getAuth(), []);
  const isAdmin = auth?.role === 'ADMIN';
  const [gestaoOpen, setGestaoOpen] = useState(false);

  const handleLogout = () => {
    clearAuth();
    router.replace('/login');
  };

  return (
    <Suspense fallback={null}>
      <AuthGuard>
        <CashRegisterProvider>
          <OrdersProvider>
            <ModulesProvider>
              <div className="min-h-screen bg-gray-100 flex flex-col">
                <header className="bg-blue-900 text-white px-6 py-3 flex items-center justify-between shadow-md relative z-50">
                  <div className="flex items-center gap-8">
                    <Link href="/" className="font-bold text-xl tracking-tight">DiPDV</Link>
                    <nav className="flex items-center gap-6 text-sm font-medium">
                      <Link href="/pdv" className="hover:text-blue-200 transition">
                        PDV
                      </Link>
                      <ModuleGate module="REPORTS">
                        <Link href="/reports" className="hover:text-blue-200 transition">
                          Relatórios
                        </Link>
                      </ModuleGate>

                      {isAdmin && (
                        <div className="relative">
                          <button
                            onClick={() => setGestaoOpen(!gestaoOpen)}
                            className="flex items-center gap-1 hover:text-blue-200 transition"
                          >
                            <Settings className="w-4 h-4" />
                            Gestão
                            <ChevronDown className={`w-3 h-3 transition-transform ${gestaoOpen ? 'rotate-180' : ''}`} />
                          </button>

                          {gestaoOpen && (
                            <div className="absolute top-full left-0 mt-2 w-48 bg-white text-gray-800 rounded-md shadow-lg border border-gray-200 py-1 flex flex-col">
                              <Link
                                href="/manage/categories"
                                className="px-4 py-2 hover:bg-gray-100 transition"
                                onClick={() => setGestaoOpen(false)}
                              >
                                Categorias
                              </Link>
                              <Link
                                href="/manage/products"
                                className="px-4 py-2 hover:bg-gray-100 transition"
                                onClick={() => setGestaoOpen(false)}
                              >
                                Produtos
                              </Link>
                              <Link
                                href="/manage/users"
                                className="px-4 py-2 hover:bg-gray-100 transition"
                                onClick={() => setGestaoOpen(false)}
                              >
                                Equipe
                              </Link>
                            </div>
                          )}
                        </div>
                      )}
                      <OpenOrdersIndicator />
                      <CashRegisterIndicator />
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
          </OrdersProvider>
        </CashRegisterProvider>
      </AuthGuard>
    </Suspense>
  );
}