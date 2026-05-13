'use client';

import React, { useEffect } from 'react';
import { Toast, ToastType } from './types';
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-react';

interface ToasterProps {
  toasts: Toast[];
  removeToast: (id: string) => void;
}

export const Toaster: React.FC<ToasterProps> = ({ toasts, removeToast }) => {
  return (
    <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 max-w-sm w-full">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onRemove={() => removeToast(toast.id)} />
      ))}
    </div>
  );
};

const ToastItem: React.FC<{ toast: Toast; onRemove: () => void }> = ({ toast, onRemove }) => {
  useEffect(() => {
    const timer = setTimeout(onRemove, 5000);
    return () => clearTimeout(timer);
  }, [onRemove]);

  const icons: Record<ToastType, React.ReactNode> = {
    success: <CheckCircle className="w-5 h-5 text-green-500" />,
    error: <XCircle className="w-5 h-5 text-red-500" />,
    warning: <AlertCircle className="w-5 h-5 text-yellow-500" />,
    info: <Info className="w-5 h-5 text-blue-500" />,
  };

  const colors: Record<ToastType, string> = {
    success: 'border-green-100 bg-green-50 text-green-800',
    error: 'border-red-100 bg-red-50 text-red-800',
    warning: 'border-yellow-100 bg-yellow-50 text-yellow-800',
    info: 'border-blue-100 bg-blue-50 text-blue-800',
  };

  return (
    <div
      className={`flex items-center gap-3 p-4 rounded-lg border shadow-lg transition-all duration-300 ${colors[toast.type]}`}
    >
      <div className="shrink-0">{icons[toast.type]}</div>
      <p className="text-sm font-medium flex-1">{toast.message}</p>
      <button
        onClick={onRemove}
        className="shrink-0 hover:opacity-70 transition-opacity"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
};
