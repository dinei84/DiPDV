'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiGet, apiPost, apiPatch, apiDelete } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import {
  Order,
  OrderSummary,
  OrderStatus,
  CreateOrderDTO,
  AddItemDTO,
  UpdateQuantityDTO,
  CancelOrderDTO,
  Page,
} from '@/lib/types';
import { toast } from '@/lib/toast';

interface OrdersContextType {
  openOrders: OrderSummary[];
  currentOrder: Order | null;
  currentOrderId: string | null;
  setCurrentOrderId: (id: string | null) => void;
  createOrder: (dto: CreateOrderDTO) => Promise<void>;
  addItem: (orderId: string, productId: string, quantity: number) => Promise<void>;
  updateQuantity: (orderId: string, itemId: string, quantity: number) => Promise<void>;
  removeItem: (orderId: string, itemId: string) => Promise<void>;
  cancelOrder: (orderId: string, reason: string) => Promise<void>;
  refresh: () => Promise<void>;
  loading: boolean;
}

const OrdersContext = createContext<OrdersContextType | undefined>(undefined);

export function OrdersProvider({ children }: { children: React.ReactNode }) {
  const [openOrders, setOpenOrders] = useState<OrderSummary[]>([]);
  const [currentOrder, setCurrentOrder] = useState<Order | null>(null);
  const [currentOrderId, setCurrentOrderId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const refresh = async () => {
    setLoading(true);
    try {
      const data = await apiGet<Page<OrderSummary>>('/api/v1/orders?status=OPEN');
      setOpenOrders(data.content ?? []);

      if (currentOrderId && !data.content?.find((o) => o.id === currentOrderId)) {
        setCurrentOrderId(null);
        setCurrentOrder(null);
      }
    } catch (error) {
      console.error('Failed to fetch orders:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadCurrentOrder = async (orderId: string) => {
    try {
      const order = await apiGet<Order>(`/api/v1/orders/${orderId}`);
      setCurrentOrder(order);
    } catch (error) {
      console.error('Failed to load order details:', error);
      setCurrentOrder(null);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    if (currentOrderId) {
      loadCurrentOrder(currentOrderId);
    } else {
      setCurrentOrder(null);
    }
  }, [currentOrderId]);

  const createOrder = async (dto: CreateOrderDTO) => {
    try {
      const newOrder = await apiPost<Order>('/api/v1/orders', dto);
      setOpenOrders((prev) => [
        ...prev,
        {
          id: newOrder.id,
          identifier: newOrder.identifier,
          status: newOrder.status,
          total: newOrder.total,
          itemCount: newOrder.items.length,
          createdAt: newOrder.createdAt,
        },
      ]);
      setCurrentOrderId(newOrder.id);
      router.push('/pdv');
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
      throw error;
    }
  };

  const addItem = async (orderId: string, productId: string, quantity: number) => {
    try {
      const dto: AddItemDTO = { productId, quantity };
      const updatedOrder = await apiPost<Order>(`/api/v1/orders/${orderId}/items`, dto);
      setCurrentOrder(updatedOrder);
      setOpenOrders((prev) =>
        prev.map((o) =>
          o.id === orderId
            ? {
                ...o,
                total: updatedOrder.total,
                itemCount: updatedOrder.items.length,
              }
            : o
        )
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
      throw error;
    }
  };

  const updateQuantity = async (orderId: string, itemId: string, quantity: number) => {
    try {
      const dto: UpdateQuantityDTO = { quantity };
      const updatedOrder = await apiPatch<Order>(
        `/api/v1/orders/${orderId}/items/${itemId}`,
        dto
      );
      setCurrentOrder(updatedOrder);
      setOpenOrders((prev) =>
        prev.map((o) =>
          o.id === orderId
            ? {
                ...o,
                total: updatedOrder.total,
                itemCount: updatedOrder.items.length,
              }
            : o
        )
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
      throw error;
    }
  };

  const removeItem = async (orderId: string, itemId: string) => {
    try {
      const updatedOrder = await apiDelete<Order>(`/api/v1/orders/${orderId}/items/${itemId}`);
      setCurrentOrder(updatedOrder);
      setOpenOrders((prev) =>
        prev.map((o) =>
          o.id === orderId
            ? {
                ...o,
                total: updatedOrder.total,
                itemCount: updatedOrder.items.length,
              }
            : o
        )
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
      throw error;
    }
  };

  const cancelOrder = async (orderId: string, reason: string) => {
    try {
      const dto: CancelOrderDTO = { reason };
      await apiPatch<Order>(`/api/v1/orders/${orderId}/cancel`, dto);
      setOpenOrders((prev) => prev.filter((o) => o.id !== orderId));
      if (currentOrderId === orderId) {
        setCurrentOrderId(null);
        setCurrentOrder(null);
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
      throw error;
    }
  };

  return (
    <OrdersContext.Provider
      value={{
        openOrders,
        currentOrder,
        currentOrderId,
        setCurrentOrderId,
        createOrder,
        addItem,
        updateQuantity,
        removeItem,
        cancelOrder,
        refresh,
        loading,
      }}
    >
      {children}
    </OrdersContext.Provider>
  );
}

export function useOrders(): OrdersContextType {
  const context = useContext(OrdersContext);
  if (!context) {
    throw new Error('useOrders must be used within an OrdersProvider');
  }
  return context;
}
