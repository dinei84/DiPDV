'use client';

import React, { useEffect, useState } from 'react';
import { Wallet } from 'lucide-react';
import { useCashRegister } from '@/lib/cash-register/CashRegisterContext';
import { apiPriceToCents, centsToBRL } from '@/lib/price';
import CashRegisterDrawer from './CashRegisterDrawer';

function formatOpenDuration(openedAt: string) {
  const totalMinutes = Math.max(0, Math.floor((Date.now() - new Date(openedAt).getTime()) / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours}h ${minutes}min`;
}

export default function CashRegisterIndicator() {
  const { currentCashRegister, isOpen, loading } = useCashRegister();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [, setTick] = useState(0);

  useEffect(() => {
    const interval = window.setInterval(() => setTick((value) => value + 1), 60000);
    return () => window.clearInterval(interval);
  }, []);

  const displayTotal = currentCashRegister
    ? apiPriceToCents(currentCashRegister.openingBalance + currentCashRegister.totalCash + currentCashRegister.totalPix)
    : 0;

  return (
    <>
      <button
        type="button"
        onClick={() => setDrawerOpen(true)}
        className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-medium transition ${
          isOpen && currentCashRegister
            ? 'border-green-300 bg-green-100 text-green-950 hover:bg-green-200'
            : 'border-white/20 bg-white/10 text-white hover:bg-white/20'
        }`}
      >
        <Wallet className="h-4 w-4" />
        {isOpen && currentCashRegister
          ? `Caixa: ${centsToBRL(displayTotal)} • há ${formatOpenDuration(currentCashRegister.openedAt)}`
          : loading
            ? 'Caixa...'
            : 'Abrir caixa'}
      </button>

      <CashRegisterDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </>
  );
}
