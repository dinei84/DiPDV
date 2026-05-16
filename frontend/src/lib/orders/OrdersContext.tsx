'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { apiGet, apiPost, apiPatch, apiDelete } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import {
  Order,
  OrderStatus,
  CreateOrderDTO,
  AddItemDTO,
  UpdateQuantityDTO,
  CancelOrderDTO,
  Page,
} from '@/lib/types';
import { toast } from '@/lib/toast';

interface OrdersContextType {
  openOrders: Order[];
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
  const [openOrders, setOpenOrders] = useState<Order[]>([]);
  const [currentOrderId, setCurrentOrderId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const currentOrder = openOrders.find((o) => o.id === currentOrderId) ?? null;

  const refresh = async () => {
    setLoading(true);
    try {
      const data = await apiGet<Page<Order>>('/api/v1/orders?status=OPEN');
      setOpenOrders(data.content ?? []);

      if (currentOrderId && !data.content?.find((o) => o.id === currentOrderId)) {
        setCurrentOrderId(null);
      }
    } catch (error) {
      console.error('Failed to fetch orders:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const createOrder = async (dto: CreateOrderDTO) => {
    try {
      const newOrder = await apiPost<Order>('/api/v1/orders', dto);
      setOpenOrders((prev) => [...prev, newOrder]);
      setCurrentOrderId(newOrder.id);
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
      setOpenOrders((prev) => prev.map((o) => (o.id === orderId ? updatedOrder : o)));
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
      setOpenOrders((prev) => prev.map((o) => (o.id === orderId ? updatedOrder : o)));
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
      setOpenOrders((prev) => prev.map((o) => (o.id === orderId ? updatedOrder : o)));
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
      const updatedOrder = await apiPatch<Order>(`/api/v1/orders/${orderId}/cancel`, dto);
      setOpenOrders((prev) => prev.filter((o) => o.id !== orderId));
      if (currentOrderId === orderId) {
        setCurrentOrderId(null);
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
