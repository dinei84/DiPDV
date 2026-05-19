'use client';

import React, { useState } from 'react';
import { Order } from '@/lib/types';

interface CancelOrderDialogProps {
  order: Order;
  onConfirm: (reason: string) => Promise<void>;
  onCancel: () => void;
}

export default function CancelOrderDialog({ order, onConfirm, onCancel }: CancelOrderDialogProps) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (reason.trim().length < 3) return;

    setSubmitting(true);
    try {
      await onConfirm(reason.trim());
    } finally {
      setSubmitting(false);
    }
  };

  const isValid = reason.trim().length >= 3;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-sm rounded-lg bg-white shadow-lg">
        <div className="border-b border-red-200 bg-red-50 px-6 py-4">
          <h2 className="text-lg font-semibold text-red-900">Cancelar comanda</h2>
          <p className="text-sm text-red-700">Esta ação é irreversível</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 p-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Motivo do cancelamento *
            </label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Explique por que a comanda está sendo cancelada..."
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-blue-500 min-h-24"
              disabled={submitting}
              autoFocus
            />
            <p className="mt-1 text-xs text-gray-500">
              Mínimo 3 caracteres
            </p>
          </div>

          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onCancel}
              disabled={submitting}
              className="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Voltar
            </button>
            <button
              type="submit"
              disabled={submitting || !isValid}
              className="flex-1 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:bg-gray-400"
            >
              Confirmar cancelamento
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
