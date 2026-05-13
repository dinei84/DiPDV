'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { Toast, ToastType } from './types';
import { toastBus } from './toast-bus';
import { Toaster } from './Toaster';

const ToastContext = createContext<{
  addToast: (message: string, type: ToastType) => void;
  removeToast: (id: string) => void;
} | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, type: ToastType) => {
    setToasts((current) => {
      // Dedup by message
      if (current.some((t) => t.message === message)) return current;
      
      const newToast = { id: Math.random().toString(36).substr(2, 9), message, type };
      const nextToasts = [...current, newToast];
      
      // Limit to 3
      if (nextToasts.length > 3) return nextToasts.slice(1);
      return nextToasts;
    });
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    return toastBus.subscribe(addToast);
  }, [addToast]);

  return (
    <ToastContext.Provider value={{ addToast, removeToast }}>
      {children}
      <Toaster toasts={toasts} removeToast={removeToast} />
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within ToastProvider');
  return {
    success: (msg: string) => context.addToast(msg, 'success'),
    error: (msg: string) => context.addToast(msg, 'error'),
    info: (msg: string) => context.addToast(msg, 'info'),
    warning: (msg: string) => context.addToast(msg, 'warning'),
  };
};
