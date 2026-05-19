'use client';

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { ApiError } from '@/lib/api-error';
import { apiGet } from '@/lib/api';
import { CashRegister } from '@/lib/types';

interface CashRegisterContextValue {
  currentCashRegister: CashRegister | null;
  isOpen: boolean;
  loading: boolean;
  refresh: () => Promise<void>;
}

const CashRegisterContext = createContext<CashRegisterContextValue | undefined>(undefined);

export const CashRegisterProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentCashRegister, setCurrentCashRegister] = useState<CashRegister | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const cashRegister = await apiGet<CashRegister>('/api/v1/cash-registers/current', {
        suppressErrorToast: true,
      });
      setCurrentCashRegister(cashRegister);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setCurrentCashRegister(null);
        return;
      }
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh().catch(() => setCurrentCashRegister(null));
  }, [refresh]);

  const value = useMemo(
    () => ({
      currentCashRegister,
      isOpen: currentCashRegister?.status === 'OPEN',
      loading,
      refresh,
    }),
    [currentCashRegister, loading, refresh]
  );

  return (
    <CashRegisterContext.Provider value={value}>
      {children}
    </CashRegisterContext.Provider>
  );
};

export const useCashRegister = () => {
  const context = useContext(CashRegisterContext);
  if (!context) throw new Error('useCashRegister must be used within CashRegisterProvider');
  return context;
};
