'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Plus } from 'lucide-react';
import { useOrders } from '@/lib/orders/OrdersContext';
import { useOrdersPolling } from '@/lib/orders/useOrdersPolling';
import { useOrderTimeRefresh } from '@/lib/orders/useOrderTimeRefresh';
import { classifyOrderTime, OrderTimeStatus } from '@/lib/orders/orderTimeThresholds';
import OrderCard from './OrderCard';
import NewOrderDialog from './NewOrderDialog';

export default function OrdersDashboard() {
  const { openOrders, setCurrentOrderId } = useOrders();
  const router = useRouter();
  const now = useOrderTimeRefresh();
  useOrdersPolling();

  const [newOrderDialogOpen, setNewOrderDialogOpen] = useState(false);

  const sorted = [...openOrders].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
  );

  const statusCounts = sorted.reduce<Record<OrderTimeStatus, number>>(
    (acc, order) => {
      acc[classifyOrderTime(order.createdAt, now)]++;
      return acc;
    },
    { fresh: 0, warning: 0, critical: 0 }
  );

  const handleSelectOrder = (id: string) => {
    setCurrentOrderId(id);
    router.push('/pdv');
  };

  const breakdownParts: string[] = [];
  if (statusCounts.critical > 0)
    breakdownParts.push(`${statusCounts.critical} atrasada${statusCounts.critical > 1 ? 's' : ''}`);
  if (statusCounts.warning > 0)
    breakdownParts.push(`${statusCounts.warning} em alerta`);
  if (statusCounts.fresh > 0)
    breakdownParts.push(`${statusCounts.fresh} ok`);

  return (
    <div className="p-4 md:p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pedidos abertos</h1>
          {openOrders.length > 0 && (
            <p className="text-sm text-gray-500 mt-1">
              {openOrders.length} comanda{openOrders.length > 1 ? 's' : ''}
              {breakdownParts.length > 0 && ` · ${breakdownParts.join(' · ')}`}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={() => setNewOrderDialogOpen(true)}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 transition"
        >
          <Plus className="h-4 w-4" />
          Nova comanda
        </button>
      </div>

      {openOrders.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 gap-4">
          <p className="text-gray-500 text-lg">Nenhuma comanda aberta no momento.</p>
          <button
            type="button"
            onClick={() => setNewOrderDialogOpen(true)}
            className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 transition"
          >
            <Plus className="h-4 w-4" />
            Criar primeira comanda
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {sorted.map((order) => (
            <OrderCard
              key={order.id}
              order={order}
              status={classifyOrderTime(order.createdAt, now)}
              now={now}
              onClick={handleSelectOrder}
            />
          ))}
        </div>
      )}

      <NewOrderDialog open={newOrderDialogOpen} onClose={() => setNewOrderDialogOpen(false)} />
    </div>
  );
}
