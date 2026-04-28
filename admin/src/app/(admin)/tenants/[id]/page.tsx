'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { TenantRequest, TenantResponse } from '@/lib/types';
import { apiGet, apiPut, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast/useToast';
import { useTenantModules } from '@/lib/hooks';
import TenantForm from '../_components/TenantForm';
import ModulesList from '../_components/ModulesList';

export default function EditTenantPage() {
  const params = useParams();
  const tenantId = params.id as string;
  const { addToast } = useToast();

  // Tenant state
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [tenantLoading, setTenantLoading] = useState(true);
  const [tenantError, setTenantError] = useState<string | null>(null);
  const [isSavingTenant, setIsSavingTenant] = useState(false);

  // Modules state
  const { modules, loading: modulesLoading, error: modulesError } =
    useTenantModules(tenantId);

  // Fetch tenant data
  useEffect(() => {
    const fetchTenant = async () => {
      try {
        setTenantLoading(true);
        setTenantError(null);
        const data = await apiGet<TenantResponse>(
          `/admin/tenants/${tenantId}`
        );
        setTenant(data);
      } catch (err) {
        const message =
          err instanceof ApiError
            ? err.message
            : 'Erro ao carregar tenant';
        setTenantError(message);
      } finally {
        setTenantLoading(false);
      }
    };

    fetchTenant();
  }, [tenantId]);

  const handleTenantSubmit = async (data: TenantRequest) => {
    try {
      setIsSavingTenant(true);
      const updated = await apiPut<TenantResponse>(
        `/admin/tenants/${tenantId}`,
        data
      );
      setTenant(updated);
      addToast('success', 'Dados salvos');
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : 'Erro ao salvar tenant';
      addToast('error', message);
    } finally {
      setIsSavingTenant(false);
    }
  };

  const handleModuleToggle = (code: string, newState: boolean) => {
    // Update local modules state to reflect optimistic change
    if (tenant) {
      if (newState) {
        // Enable: add to enabledModules if not already there
        if (!tenant.enabledModules.includes(code)) {
          setTenant({
            ...tenant,
            enabledModules: [...tenant.enabledModules, code],
          });
        }
      } else {
        // Disable: remove from enabledModules
        setTenant({
          ...tenant,
          enabledModules: tenant.enabledModules.filter((m) => m !== code),
        });
      }
    }
  };

  const isLoading = tenantLoading || modulesLoading;
  const error = tenantError || modulesError;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <h1 className="text-3xl font-bold">Carregando...</h1>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1">
            <div className="bg-white rounded-lg shadow p-6">
              <div className="space-y-4">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-10 bg-gray-300 rounded animate-pulse" />
                ))}
              </div>
            </div>
          </div>
          <div className="lg:col-span-2">
            <div className="bg-white rounded-lg shadow p-6">
              <div className="space-y-4">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-16 bg-gray-300 rounded animate-pulse" />
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <h1 className="text-3xl font-bold">Tenant</h1>
        <div className="bg-white border-l-4 border-red-600 rounded-lg shadow p-6">
          <p className="text-gray-700">
            Não foi possível carregar os dados. Tente recarregar a página.
          </p>
        </div>
      </div>
    );
  }

  if (!tenant) {
    return (
      <div className="space-y-4">
        <h1 className="text-3xl font-bold">Tenant</h1>
        <div className="bg-white rounded-lg shadow p-6">
          <p className="text-gray-500">Tenant não encontrado.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-3xl font-bold">{tenant.name}</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Tenant Data Section */}
        <div className="lg:col-span-1">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">
              Dados do Tenant
            </h2>
            <TenantForm
              initialData={{
                name: tenant.name,
                slug: tenant.slug,
                active: tenant.active,
              }}
              onSubmit={handleTenantSubmit}
              submitLabel="Salvar"
              isLoading={isSavingTenant}
            />
          </div>
        </div>

        {/* Modules Section */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">
              Módulos
            </h2>
            {modules.length === 0 ? (
              <p className="text-sm text-gray-500">
                Nenhum módulo disponível.
              </p>
            ) : (
              <ModulesList
                modules={modules}
                tenantId={tenantId}
                onModuleToggle={handleModuleToggle}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
