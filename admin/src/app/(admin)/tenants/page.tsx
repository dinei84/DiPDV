'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { TenantResponse } from '@/lib/types';
import { apiGet, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast/useToast';
import Link from 'next/link';

export default function TenantsPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [tenants, setTenants] = useState<TenantResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchTenants = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await apiGet<TenantResponse[]>('/api/v1/admin/tenants');
        setTenants(data);
      } catch (err) {
        const message =
          err instanceof ApiError
            ? err.message
            : 'Erro ao carregar tenants';
        setError(message);
      } finally {
        setLoading(false);
      }
    };

    fetchTenants();
  }, []);

  const handleRowClick = (tenantId: string) => {
    router.push(`/tenants/${tenantId}`);
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold">Tenants</h1>
          <div className="h-10 w-32 bg-gray-300 rounded animate-pulse" />
        </div>
        <div className="bg-white rounded-lg shadow p-6">
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex gap-4">
                <div className="h-8 flex-1 bg-gray-300 rounded animate-pulse" />
                <div className="h-8 w-32 bg-gray-300 rounded animate-pulse" />
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold">Tenants</h1>
          <Link
            href="/tenants/new"
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            Novo tenant
          </Link>
        </div>
        <div className="bg-white border-l-4 border-red-600 rounded-lg shadow p-6">
          <p className="text-gray-700">
            Não foi possível carregar tenants. Tente recarregar a página.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold">Tenants</h1>
        <Link
          href="/tenants/new"
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Novo tenant
        </Link>
      </div>

      {tenants.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-6 text-center text-gray-500">
          <p>Nenhum tenant criado ainda.</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                  Nome
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                  Slug
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                  Módulos Ativos
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">
                  Ações
                </th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {tenants.map((tenant) => (
                <tr
                  key={tenant.id}
                  onClick={() => handleRowClick(tenant.id)}
                  className="hover:bg-gray-50 cursor-pointer transition-colors"
                >
                  <td className="px-6 py-4 text-sm text-gray-900">
                    {tenant.name}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {tenant.slug}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <span
                      className={`inline-block px-3 py-1 rounded-full text-xs font-semibold ${
                        tenant.active
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {tenant.active ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {tenant.enabledModules.length}
                  </td>
                  <td className="px-6 py-4 text-sm">
                    <Link
                      href={`/tenants/${tenant.id}`}
                      className="text-blue-600 hover:text-blue-700 font-medium"
                      onClick={(e) => e.stopPropagation()}
                    >
                      Editar
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
