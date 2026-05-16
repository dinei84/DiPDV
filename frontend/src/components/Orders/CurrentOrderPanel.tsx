'use client';

import React, { useState } from 'react';
import { Trash2, Plus, Minus } from 'lucide-react';
import { useOrders } from '@/lib/orders/OrdersContext';
import { apiPriceToBRL } from '@/lib/price';
import { ConfirmDialog } from '@/lib/confirm/ConfirmDialog';
import CancelOrderDialog from './CancelOrderDialog';
import NewOrderDialog from './NewOrderDialog';
import PaymentDialog from './PaymentDialog';

interface CurrentOrderPanelProps {
  onNewOrderClick: () => void;
}

export default function CurrentOrderPanel({ onNewOrderClick }: CurrentOrderPanelProps) {
  const { currentOrder, updateQuantity, removeItem, cancelOrder } = useOrders();
  const [itemToRemove, setItemToRemove] = useState<{ orderId: string; itemId: string } | null>(null);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);

  if (!currentOrder) return null;

  const formatIdentifier = (identifier: string | null) => {
    if (identifier) return identifier;
    return `Anônimo #${currentOrder.createdAt.slice(-4)}`;
  };

  const handleQuantityChange = async (itemId: string, newQuantity: number) => {
    if (newQuantity < 1) return;
    try {
      await updateQuantity(currentOrder.id, itemId, newQuantity);
    } catch (error) {
      // Error handled by context
    }
  };

  const handleRemoveItem = async (itemId: string) => {
    try {
      await removeItem(currentOrder.id, itemId);
      setItemToRemove(null);
    } catch (error) {
      // Error handled by context
    }
  };

  const handleCancel = async (reason: string) => {
    try {
      await cancelOrder(currentOrder.id, reason);
      setCancelDialogOpen(false);
    } catch (error) {
      // Error handled by context
    }
  };

  return (
    <>
      <div className="flex flex-col gap-4 rounded-lg border border-gray-200 bg-white p-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-200 pb-4">
          <div>
            <h3 className="text-lg font-semibold text-gray-900">
              {formatIdentifier(currentOrder.identifier)}
            </h3>
            <p className="text-xs text-gray-500">
              {new Date(currentOrder.createdAt).toLocaleTimeString('pt-BR')}
            </p>
          </div>
          <button
            onClick={() => setCancelDialogOpen(true)}
            className="rounded-full p-2 text-red-600 hover:bg-red-50"
            title="Cancelar comanda"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>

        {/* Items */}
        <div className="flex-1 space-y-2 overflow-y-auto max-h-96">
          {currentOrder.items.length === 0 ? (
            <p className="py-8 text-center text-sm text-gray-500">Nenhum item adicionado</p>
          ) : (
            currentOrder.items.map((item) => (
              <div key={item.id} className="flex items-center gap-3 rounded-lg bg-gray-50 p-3">
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-900">{item.productName}</p>
                  <p className="text-xs text-gray-500">{apiPriceToBRL(item.unitPrice)}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleQuantityChange(item.id, item.quantity - 1)}
                    className="rounded-md bg-white border border-gray-300 p-1 text-gray-500 hover:text-gray-900"
                    title="Diminuir quantidade"
                  >
                    <Minus className="h-3 w-3" />
                  </button>
                  <span className="w-6 text-center text-sm font-medium text-gray-900">
                    {item.quantity}
                  </span>
                  <button
                    onClick={() => handleQuantityChange(item.id, item.quantity + 1)}
                    className="rounded-md bg-white border border-gray-300 p-1 text-gray-500 hover:text-gray-900"
                    title="Aumentar quantidade"
                  >
                    <Plus className="h-3 w-3" />
                  </button>
                </div>
                <div className="min-w-16 text-right">
                  <p className="text-sm font-semibold text-gray-900">
                    {apiPriceToBRL(item.totalPrice)}
                  </p>
                </div>
                <button
                  onClick={() => setItemToRemove({ orderId: currentOrder.id, itemId: item.id })}
                  className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600"
                  title="Remover item"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="space-y-3 border-t border-gray-200 pt-4">
          <div className="flex items-baseline justify-between">
            <span className="text-sm text-gray-600">Total</span>
            <span className="text-2xl font-bold text-gray-900">
              {apiPriceToBRL(currentOrder.total)}
            </span>
          </div>

          <button
            onClick={onNewOrderClick}
            className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50"
          >
            + Nova comanda
          </button>

          <button
            onClick={() => setPaymentOpen(true)}
            disabled={currentOrder.items.length === 0}
            className="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Fechar comanda
          </button>
        </div>
      </div>

      {/* Confirm remove item dialog */}
      {itemToRemove && currentOrder.items.find((i) => i.id === itemToRemove.itemId) && (
        <ConfirmDialog
          title="Remover item"
          message={`Remover "${currentOrder.items.find((i) => i.id === itemToRemove.itemId)?.productName}" da comanda?`}
          onConfirm={() => handleRemoveItem(itemToRemove.itemId)}
          onCancel={() => setItemToRemove(null)}
          danger={false}
        />
      )}

      {/* Cancel order dialog */}
      {cancelDialogOpen && (
        <CancelOrderDialog
          order={currentOrder}
          onConfirm={handleCancel}
          onCancel={() => setCancelDialogOpen(false)}
        />
      )}

      {/* Payment dialog */}
      <PaymentDialog
        order={currentOrder}
        isOpen={paymentOpen}
        onClose={() => setPaymentOpen(false)}
      />
    </>
  );
}
