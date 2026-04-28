'use client';

import { createContext } from 'react';
import { Toast } from '@/lib/types';

export interface ToastContextType {
  toasts: Toast[];
  addToast: (type: 'success' | 'error' | 'warning' | 'info', message: string) => void;
  removeToast: (id: string) => void;
}

export const ToastContext = createContext<ToastContextType | null>(null);
