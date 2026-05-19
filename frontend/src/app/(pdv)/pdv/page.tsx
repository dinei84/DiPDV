'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { getAuth } from '@/lib/auth';
import { useCashRegister } from '@/lib/cash-register/CashRegisterContext';
import { useOrders } from '@/lib/orders/OrdersContext';
import CatalogGrid from '@/components/Orders/CatalogGrid';
import CurrentOrderPanel from '@/components/Orders/CurrentOrderPanel';
import NewOrderDialog from '@/components/Orders/NewOrderDialog';
import CashRegisterDrawer from '@/components/CashRegister/CashRegisterDrawer';

export default function PDVPage() {
  const router = useRouter();
  const auth = getAuth();
  const { currentCashRegister } = useCashRegister();
  const { openOrders } = useOrders();
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [newOrderDialogOpen, setNewOrderDialogOpen] = useState(false);
  const [cashDrawerOpen, setCashDrawerOpen] = useState(false);

  // Auth guard
  if (!auth) {
    router.replace('/');
    return null;
  }

  // Role check
  if (!['ADMIN', 'MANAGER', 'CASHIER'].includes(auth.role)) {
    router.replace('/');
    return null;
  }

  // Cash register gate
  if (!currentCashRegister?.id) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="w-full max-w-md space-y-6 text-center">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              Abra o caixa para iniciar
            </h1>
            <p className="mt-2 text-gray-500">
              Você precisa abrir o caixa antes de começar a atender
            </p>
          </div>
          <button
            onClick={() => setCashDrawerOpen(true)}
            className="w-full rounded-md bg-blue-600 px-4 py-3 text-sm font-semibold text-white hover:bg-blue-700"
          >
            Abrir caixa
          </button>
        </div>
        <CashRegisterDrawer open={cashDrawerOpen} onClose={() => setCashDrawerOpen(false)} />
      </div>
    );
  }

  // Empty state: caixa aberto, sem comandas
  if (openOrders.length === 0) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="w-full max-w-md space-y-6 text-center">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              Bem-vindo ao PDV
            </h1>
            <p className="mt-2 text-gray-500">
              Crie uma nova comanda para começar a atender
            </p>
          </div>
          <button
            onClick={() => setNewOrderDialogOpen(true)}
            className="w-full rounded-md bg-blue-600 px-4 py-3 text-sm font-semibold text-white hover:bg-blue-700"
          >
            + Nova comanda
          </button>
        </div>
        <NewOrderDialog open={newOrderDialogOpen} onClose={() => setNewOrderDialogOpen(false)} />
      </div>
    );
  }

  // Full layout: catálogo + comanda
  return (
    <div className="flex h-screen flex-col gap-4 bg-gray-50 p-4">
      <div className="flex flex-1 gap-4 overflow-hidden">
        {/* Catálogo - esquerda */}
        <div className="flex-[2] overflow-hidden lg:flex-[65%]">
          <CatalogGrid
            selectedCategoryId={selectedCategoryId}
            onSelectCategory={setSelectedCategoryId}
          />
        </div>

        {/* Comanda - direita */}
        <div className="hidden lg:flex lg:flex-[35%] lg:overflow-hidden">
          <CurrentOrderPanel onNewOrderClick={() => setNewOrderDialogOpen(true)} />
        </div>
      </div>

      {/* Mobile comanda drawer button */}
      <div className="lg:hidden">
        {/* TODO: Implementar drawer de comanda para mobile */}
      </div>

      <NewOrderDialog open={newOrderDialogOpen} onClose={() => setNewOrderDialogOpen(false)} />
    </div>
  );
}
