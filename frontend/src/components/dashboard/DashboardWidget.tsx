'use client';
import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';
import { getAuth } from '@/lib/auth';
import PaymentChart from './PaymentChart';

interface Summary {
  orderCount: number;
  totalRevenue: number;
  avgTicket: number;
}

interface PaymentMethod {
  method: string;
  transactionCount: number;
  totalAmount: number;
}

export default function DashboardWidget() {
  const auth = getAuth();
  const canViewReports =
    auth?.role === 'ADMIN' || auth?.role === 'MANAGER';

  const [summary, setSummary] = useState<Summary | null>(null);
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!canViewReports) {
      setLoading(false);
      return;
    }

    const today = new Date().toISOString().split('T')[0];
    Promise.all([
      apiFetch<Summary>(
        `/api/v1/reports/summary?from=${today}&to=${today}`
      ),
      apiFetch<PaymentMethod[]>(
        `/api/v1/reports/payment-methods?from=${today}&to=${today}`
      ),
    ])
      .then(([s, m]) => {
        setSummary(s);
        setMethods(m);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [canViewReports]);

  if (!canViewReports) return null;
  if (loading)
    return (
      <div className="text-sm text-gray-400 p-4">
        Carregando dashboard...
      </div>
    );

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4 bg-gray-50 rounded-xl mb-4">
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Pedidos Hoje
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          {summary?.orderCount ?? 0}
        </p>
      </div>
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Faturamento Hoje
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          R$ {summary?.totalRevenue.toFixed(2) ?? '0.00'}
        </p>
      </div>
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Ticket Médio
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          R$ {summary?.avgTicket.toFixed(2) ?? '0.00'}
        </p>
      </div>

      {methods.length > 0 && (
        <div className="md:col-span-3 bg-white rounded-lg p-4 shadow-sm">
          <p className="text-sm font-medium text-gray-700 mb-3">
            Faturamento por Forma de Pagamento
          </p>
          <PaymentChart data={methods} />
        </div>
      )}
    </div>
  );
}
