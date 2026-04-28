'use client';

import { useState, useEffect, ReactNode, useCallback } from 'react';
import { Toast } from '@/lib/types';
import { ToastContext, ToastContextType } from './ToastContext';

interface ToastProviderProps {
  children: ReactNode;
}

export function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((type: 'success' | 'error' | 'warning' | 'info', message: string) => {
    setToasts((prevToasts) => {
      // Deduplication: check if same type + message exists
      const existingIndex = prevToasts.findIndex((t) => t.type === type && t.message === message);

      let newToasts = [...prevToasts];

      // If exists, remove it first (to reset timer)
      if (existingIndex !== -1) {
        newToasts.splice(existingIndex, 1);
      }

      // Create new toast with random ID
      const newToast: Toast = {
        id: `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        type,
        message,
        duration: 4000,
      };

      // Add new toast
      newToasts.push(newToast);

      // Keep only last 3 toasts - discard oldest if 4+
      if (newToasts.length > 3) {
        newToasts = newToasts.slice(-3);
      }

      return newToasts;
    });
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prevToasts) => prevToasts.filter((t) => t.id !== id));
  }, []);

  // Auto-dismiss after 4s
  useEffect(() => {
    if (toasts.length === 0) return;

    const timers = toasts.map((toast) => {
      return setTimeout(() => {
        removeToast(toast.id);
      }, toast.duration || 4000);
    });

    return () => {
      timers.forEach((timer) => clearTimeout(timer));
    };
  }, [toasts, removeToast]);

  const value: ToastContextType = {
    toasts,
    addToast,
    removeToast,
  };

  return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>;
}
