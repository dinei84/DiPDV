'use client';

import React, { useState } from 'react';
import { useOrders } from '@/lib/orders/OrdersContext';

interface NewOrderDialogProps {
  open: boolean;
  onClose: () => void;
}

export default function NewOrderDialog({ open, onClose }: NewOrderDialogProps) {
  const { createOrder } = useOrders();
  const [identifier, setIdentifier] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createOrder({
        identifier: identifier.trim() || null,
      });
      setIdentifier('');
      onClose();
    } catch (error) {
      // Error already handled by context
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-sm rounded-lg bg-white shadow-lg">
        <div className="border-b border-gray-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Nova comanda</h2>
          <p className="text-sm text-gray-500">Crie uma nova comanda para começar a atender</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 p-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Identificador (opcional)
            </label>
            <input
              type="text"
              maxLength={100}
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="Ex: Mesa 5, Caetano, balcão"
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-blue-500"
              autoFocus
              disabled={submitting}
            />
            <p className="mt-1 text-xs text-gray-500">
              Use para diferenciar comandas durante o turno
            </p>
          </div>

          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
            >
              Criar comanda
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
