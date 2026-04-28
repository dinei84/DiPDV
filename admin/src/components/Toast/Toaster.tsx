'use client';

import { useContext } from 'react';
import { ToastContext } from './ToastContext';

export function Toaster() {
  const context = useContext(ToastContext);

  if (!context) {
    return null;
  }

  const { toasts, removeToast } = context;

  const getColorClass = (type: string) => {
    switch (type) {
      case 'success':
        return 'bg-green-600';
      case 'error':
        return 'bg-red-600';
      case 'warning':
        return 'bg-yellow-600';
      case 'info':
        return 'bg-blue-600';
      default:
        return 'bg-gray-600';
    }
  };

  return (
    <div className="fixed bottom-4 right-4 z-50 space-y-2">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`${getColorClass(toast.type)} text-white px-6 py-3 rounded-lg shadow-lg flex items-center justify-between gap-4 animate-slide-in max-w-sm`}
        >
          <p className="flex-1">{toast.message}</p>
          <button
            onClick={() => removeToast(toast.id)}
            className="flex-shrink-0 font-bold hover:opacity-80 transition-opacity text-xl leading-none"
            aria-label="Close notification"
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
