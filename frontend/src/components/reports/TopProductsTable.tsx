'use client';

interface Product {
  productName: string;
  totalQty: number;
  totalRevenue: number;
}

export default function TopProductsTable({ data }: { data: Product[] }) {
  if (!data.length)
    return (
      <p className="text-gray-400 text-sm">
        Nenhum produto vendido no período.
      </p>
    );

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="bg-blue-900 text-white">
          <th className="p-3 text-left">Produto</th>
          <th className="p-3 text-right">Qtd Vendida</th>
          <th className="p-3 text-right">Faturamento</th>
        </tr>
      </thead>
      <tbody>
        {data.map((p, i) => (
          <tr key={i} className={i % 2 === 0 ? 'bg-gray-50' : 'bg-white'}>
            <td className="p-3">{p.productName}</td>
            <td className="p-3 text-right">{p.totalQty}</td>
            <td className="p-3 text-right">R$ {p.totalRevenue.toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
