import { useEffect } from 'react';
import { useOrders } from './OrdersContext';

export function useOrdersPolling(intervalMs: number = 60_000): void {
  const { refresh } = useOrders();

  useEffect(() => {
    const id = setInterval(refresh, intervalMs);
    return () => clearInterval(id);
  }, [refresh, intervalMs]);
}
