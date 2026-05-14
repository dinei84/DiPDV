'use client';

import React, { createContext, useContext, useState, useCallback, useRef } from 'react';
import { ConfirmDialog } from './ConfirmDialog';

interface ConfirmOptions {
  title: string;
  message: string;
  danger?: boolean;
}

type ConfirmContextType = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<ConfirmContextType | undefined>(undefined);

export const ConfirmProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolveRef = useRef<(value: boolean) => void>(() => {});

  const confirm = useCallback((options: ConfirmOptions) => {
    setOptions(options);
    setIsOpen(true);
    return new Promise<boolean>((resolve) => {
      resolveRef.current = resolve;
    });
  }, []);

  const handleClose = (result: boolean) => {
    setIsOpen(false);
    resolveRef.current(result);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {isOpen && options && (
        <ConfirmDialog
          title={options.title}
          message={options.message}
          danger={options.danger}
          onConfirm={() => handleClose(true)}
          onCancel={() => handleClose(false)}
        />
      )}
    </ConfirmContext.Provider>
  );
};

export const useConfirm = () => {
  const context = useContext(ConfirmContext);
  if (!context) throw new Error('useConfirm must be used within ConfirmProvider');
  return context;
};
