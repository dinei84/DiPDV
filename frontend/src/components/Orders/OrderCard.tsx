'use client';

import React from 'react';
import { OrderSummary } from '@/lib/types';
import { apiPriceToBRL } from '@/lib/price';
import { OrderTimeStatus } from '@/lib/orders/orderTimeThresholds';

interface OrderCardProps {
  order: OrderSummary;
  status: OrderTimeStatus;
  now: Date;
  onClick: (id: string) => void;
}

const statusStyles: Record<OrderTimeStatus, string> = {
  fresh: 'border-l-green-500 bg-white',
  warning: 'border-l-yellow-500 bg-yellow-50',
  critical: 'border-l-red-500 bg-red-50',
};

function formatElapsed(createdAt: string, now: Date): string {
  const minutes = Math.floor((now.getTime() - new Date(createdAt).getTime()) / 60000);
  if (minutes < 1) return 'agora';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rem = minutes % 60;
  return rem > 0 ? `${hours}h ${rem}min` : `${hours}h`;
}

function formatIdentifier(identifier: string | null, createdAt: string): string {
  if (identifier) return identifier;
  return `Anônimo #${createdAt.slice(-4)}`;
}

export default function OrderCard({ order, status, now, onClick }: OrderCardProps) {
  const elapsed = formatElapsed(order.createdAt, now);
  const label = formatIdentifier(order.identifier, order.createdAt);

  return (
    <button
      type="button"
      onClick={() => onClick(order.id)}
      aria-label={`Comanda ${label}, ${elapsed}, ${apiPriceToBRL(order.total)}`}
      className={`w-full text-left rounded-lg border-l-4 border border-gray-200 p-4 shadow-sm hover:shadow-md transition focus:outline-none focus:ring-2 focus:ring-blue-500 ${statusStyles[status]}`}
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <p className="font-semibold text-gray-900 text-sm truncate">{label}</p>
        <span className="text-xs text-gray-500 whitespace-nowrap">{elapsed}</span>
      </div>

      <div className="flex items-center justify-between text-sm">
        <span className="text-gray-500">{order.itemCount} item(ns)</span>
        <span className="font-bold text-gray-900">{apiPriceToBRL(order.total)}</span>
      </div>

      {status === 'critical' && (
        <p className="mt-2 text-xs font-semibold text-red-600">Atrasada</p>
      )}
      {status === 'warning' && (
        <p className="mt-2 text-xs font-semibold text-yellow-700">Atenção</p>
      )}
    </button>
  );
}
