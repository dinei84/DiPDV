'use client';

import React from 'react';
import { X, Plus } from 'lucide-react';
import { useOrders } from '@/lib/orders/OrdersContext';
import { apiPriceToBRL } from '@/lib/price';

interface OpenOrdersDrawerProps {
  open: boolean;
  onClose: () => void;
  onNewOrder: () => void;
}

export default function OpenOrdersDrawer({ open, onClose, onNewOrder }: OpenOrdersDrawerProps) {
  const { openOrders, setCurrentOrderId } = useOrders();

  if (!open) return null;

  const handleSelectOrder = (id: string) => {
    setCurrentOrderId(id);
    onClose();
  };

  const formatIdentifier = (identifier: string | null, createdAt: string) => {
    if (identifier) return identifier;
    return `Anônimo #${createdAt.slice(-4)}`;
  };

  const formatDuration = (createdAt: string) => {
    const minutes = Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000);
    if (minutes < 1) return 'agora';
    if (minutes === 1) return '1 min';
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    return hours === 1 ? '1 hora' : `${hours} horas`;
  };

  return (
    <div className="fixed inset-0 z-[8000]">
      <button
        type="button"
        aria-label="Fechar painel de comandas"
        onClick={onClose}
        className="absolute inset-0 bg-black/40"
      />

      <aside className="absolute right-0 top-0 flex h-full w-full max-w-sm flex-col bg-white shadow-2xl">
        <header className="flex items-center justify-between border-b border-gray-200 p-5">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Comandas abertas</h2>
            <p className="text-sm text-gray-500">{openOrders.length} comanda(s)</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-gray-500 hover:bg-gray-100"
            aria-label="Fechar"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-5 space-y-2">
          <button
            type="button"
            onClick={onNewOrder}
            className="w-full flex items-center justify-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 mb-4"
          >
            <Plus className="h-4 w-4" />
            Nova comanda
          </button>

          {openOrders.length === 0 ? (
            <p className="text-center text-sm text-gray-500 py-8">
              Nenhuma comanda aberta
            </p>
          ) : (
            openOrders.map((order) => (
              <button
                key={order.id}
                onClick={() => handleSelectOrder(order.id)}
                className="w-full text-left rounded-lg border border-gray-200 p-3 hover:bg-blue-50 hover:border-blue-200 transition"
              >
                <div className="flex items-start justify-between gap-2 mb-1">
                  <p className="font-medium text-gray-900">
                    {formatIdentifier(order.identifier, order.createdAt)}
                  </p>
                  <span className="text-xs text-gray-500">
                    {formatDuration(order.createdAt)}
                  </span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">{order.itemCount} item(ns)</span>
                  <span className="font-semibold text-gray-900">
                    {apiPriceToBRL(order.total)}
                  </span>
                </div>
              </button>
            ))
          )}
        </div>
      </aside>
    </div>
  );
}
