'use client';

import { useContext } from 'react';
import { ToastContext, ToastContextType } from './ToastContext';

export function useToast(): ToastContextType {
  const context = useContext(ToastContext);

  if (context === null) {
    throw new Error('useToast called outside ToastProvider');
  }

  return context;
}
