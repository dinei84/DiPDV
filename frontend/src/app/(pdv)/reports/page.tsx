'use client';

import { useEffect, useState } from 'react';
import ReportFilters from '@/components/reports/ReportFilters';
import TopProductsTable from '@/components/reports/TopProductsTable';
import { apiFetch, apiFetchBlob } from '@/lib/api';

interface TopProduct {
  productName: string;
  totalQty: number;
  totalRevenue: number;
}

export default function ReportsPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [topProducts, setTopProducts] = useState<TopProduct[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];

    setFrom(today);
    setTo(today);
  }, []);

  async function loadReports() {
    if (!from || !to) return;

    setLoading(true);
    try {
      const products = await apiFetch<TopProduct[]>(
        `/api/v1/reports/top-products?from=${from}&to=${to}&limit=10`
      );
      setTopProducts(products);
    } catch (error) {
      console.error(error);
      setTopProducts([]);
    } finally {
      setLoading(false);
    }
  }

  async function downloadPdf() {
    if (!from || !to) return;

    try {
      const blob = await apiFetchBlob(
        `/api/v1/reports/summary/pdf?from=${from}&to=${to}`
      );
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');

      link.href = objectUrl;
      link.setAttribute('download', 'relatorio-vendas.pdf');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      console.error(error);
    }
  }

  return (
    <div className="p-6 bg-white rounded-xl shadow-sm">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-blue-900">Relatórios</h1>
        <button
          onClick={downloadPdf}
          className="bg-blue-900 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-800 transition"
        >
          Exportar PDF
        </button>
      </div>

      <ReportFilters
        from={from}
        to={to}
        onFromChange={setFrom}
        onToChange={setTo}
        onApply={loadReports}
      />

      {loading ? (
        <p className="text-gray-400 text-sm mt-4">Carregando...</p>
      ) : (
        <TopProductsTable data={topProducts} />
      )}
    </div>
  );
}
