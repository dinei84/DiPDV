'use client';

import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Order, PaymentMethod, RegisterPaymentDTO } from '@/lib/types';
import { useOrders } from '@/lib/orders/OrdersContext';
import { apiPriceToBRL, apiPriceToCents, centsToApiString } from '@/lib/price';
import { toast } from '@/lib/toast';
import { ConfirmDialog } from '@/lib/confirm/ConfirmDialog';
import { MoneyInput } from '@/components/MoneyInput';
import { apiPatch, apiPost } from '@/lib/api';

interface PaymentDialogProps {
  order: Order;
  isOpen: boolean;
  onClose: () => void;
}

interface PendingPayment {
  id: string;
  method: PaymentMethod;
  amountCents: number;
  idempotencyKey: string;
}

export default function PaymentDialog({ order, isOpen, onClose }: PaymentDialogProps) {
  const { refresh } = useOrders();
  const [pendingPayments, setPendingPayments] = useState<PendingPayment[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>('CASH');
  const [amountCents, setAmountCents] = useState(0);
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen) return null;

  const totalCents = apiPriceToCents(order.total);
  const paidCents = pendingPayments.reduce((sum, p) => sum + p.amountCents, 0);
  const remainingCents = totalCents - paidCents;
  const isQuitado = paidCents >= totalCents;

  const handleAddPayment = () => {
    if (amountCents === 0) {
      toast.error('Informe o valor');
      return;
    }

    if (selectedMethod === 'PIX' && amountCents > remainingCents) {
      toast.error('PIX não permite valor maior que o restante');
      return;
    }

    if (selectedMethod === 'CARD') {
      toast.error('Cartão não está disponível nesta versão');
      return;
    }

    const newPayment: PendingPayment = {
      id: crypto.randomUUID(),
      method: selectedMethod,
      amountCents,
      idempotencyKey: crypto.randomUUID().toString(),
    };

    setPendingPayments([...pendingPayments, newPayment]);
    setAmountCents(0);
    setShowAddForm(false);
  };

  const handleRemovePayment = (id: string) => {
    setPendingPayments(pendingPayments.filter((p) => p.id !== id));
  };

  const formatIdentifier = (identifier: string | null) => {
    if (identifier) return identifier;
    return `Anônimo #${order.createdAt.slice(-4)}`;
  };

  const handleSubmit = async () => {
    setShowConfirm(false);
    setSubmitting(true);

    try {
      // 1. Close order
      await apiPatch(`/api/v1/orders/${order.id}/close`, {});

      // 2. Register payments
      for (const payment of pendingPayments) {
        const dto: RegisterPaymentDTO = {
          orderId: order.id,
          method: payment.method,
          amount: centsToApiString(payment.amountCents),
          idempotencyKey: payment.idempotencyKey,
        };
        await apiPost('/api/v1/payments', dto);
      }

      toast.success('Pagamento concluído');
      setPendingPayments([]);
      refresh();
      onClose();
    } catch (error) {
      // Error already handled by apiPost/apiPatch toast
      setSubmitting(false);
    }
  };

  const getCashChange = (): number => {
    if (selectedMethod !== 'CASH') return 0;
    const change = amountCents - remainingCents;
    return change > 0 ? change : 0;
  };

  return (
    <div className="fixed inset-0 z-[8000] flex items-center justify-center">
      <button
        type="button"
        onClick={onClose}
        className="absolute inset-0 bg-black/40"
        aria-label="Fechar diálogo"
      />

      <div className="relative w-full max-w-md rounded-lg bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">
              Pagamento — {formatIdentifier(order.identifier)}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-gray-500 hover:bg-gray-100"
            aria-label="Fechar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Subtotal */}
        <div className="mb-6 text-center">
          <p className="text-sm text-gray-500 mb-1">Subtotal</p>
          <p className="text-3xl font-bold text-gray-900">{apiPriceToBRL(order.total)}</p>
        </div>

        {/* Pending payments list */}
        <div className="mb-6 space-y-2 max-h-32 overflow-y-auto">
          {pendingPayments.map((payment) => (
            <div
              key={payment.id}
              className="flex items-center justify-between rounded-lg bg-gray-50 p-3"
            >
              <div>
                <p className="text-sm font-medium text-gray-900">{payment.method}</p>
                <p className="text-xs text-gray-500">
                  {apiPriceToBRL(payment.amountCents / 100)}
                </p>
                {payment.method === 'CASH' && (() => {
                  const paidBefore = paidCents - payment.amountCents;
                  const remainingBefore = totalCents - paidBefore;
                  const change = payment.amountCents > remainingBefore ? payment.amountCents - remainingBefore : 0;
                  return change > 0 ? (
                    <p className="text-xs text-green-600 font-medium">
                      Troco: {apiPriceToBRL(change / 100)}
                    </p>
                  ) : null;
                })()}
              </div>
              <button
                type="button"
                onClick={() => handleRemovePayment(payment.id)}
                className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600"
                aria-label="Remover pagamento"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>

        {/* Add payment form */}
        {showAddForm ? (
          <div className="mb-6 rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Método de pagamento
              </label>
              <select
                value={selectedMethod}
                onChange={(e) => setSelectedMethod(e.target.value as PaymentMethod)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
              >
                <option value="CASH">Dinheiro</option>
                <option value="PIX">PIX</option>
                <option value="CARD" disabled>
                  Cartão (Em breve)
                </option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Valor
              </label>
              <MoneyInput
                value={amountCents}
                onChange={setAmountCents}
                disabled={submitting}
              />
            </div>

            {selectedMethod === 'CASH' && amountCents > remainingCents && (
              <div className="rounded-lg bg-green-50 border border-green-200 p-3">
                <p className="text-sm text-green-800 font-medium">
                  Troco: {apiPriceToBRL((amountCents - remainingCents) / 100)}
                </p>
              </div>
            )}

            {selectedMethod === 'PIX' && amountCents > remainingCents && (
              <div className="rounded-lg bg-red-50 border border-red-200 p-3">
                <p className="text-sm text-red-800 font-medium">
                  PIX não permite valor maior que o restante
                </p>
              </div>
            )}

            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowAddForm(false);
                  setAmountCents(0);
                }}
                className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleAddPayment}
                disabled={submitting}
                className="flex-1 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                Adicionar à lista
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setShowAddForm(true)}
            disabled={isQuitado || submitting}
            className="mb-6 w-full rounded-md border border-blue-600 px-3 py-2 text-sm font-medium text-blue-600 hover:bg-blue-50 disabled:opacity-50"
          >
            + Adicionar pagamento
          </button>
        )}

        {/* Footer - Remaining or Quitado */}
        <div className="mb-6 rounded-lg bg-gray-50 p-4">
          {isQuitado ? (
            <div className="flex items-center justify-center rounded-lg bg-green-50 border border-green-200 p-3">
              <span className="text-sm font-semibold text-green-800">✓ Quitado</span>
            </div>
          ) : (
            <p className="text-center text-sm text-gray-600">
              Restante:{' '}
              <span className="font-semibold text-gray-900">
                {apiPriceToBRL(remainingCents / 100)}
              </span>
            </p>
          )}
        </div>

        {/* Action buttons */}
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => setShowConfirm(true)}
            disabled={!isQuitado || submitting || pendingPayments.length === 0}
            className="flex-1 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Processando...' : 'Confirmar e fechar comanda'}
          </button>
        </div>
      </div>

      {/* Confirmation dialog */}
      {showConfirm && (
        <ConfirmDialog
          title="Confirmar pagamento"
          message="O pagamento será processado e não poderá ser desfeito. Deseja continuar?"
          danger={true}
          onConfirm={handleSubmit}
          onCancel={() => setShowConfirm(false)}
        />
      )}
    </div>
  );
}
