'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { TenantRequest, TenantResponse } from '@/lib/types';
import { apiGet, apiPut, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast/useToast';
import { useTenantModules } from '@/lib/hooks';
import { getProtectedTenantReason } from '@/lib/utils/tenantProtection';
import TenantForm from '../_components/TenantForm';
import ModulesList from '../_components/ModulesList';
import ConfirmModal from '@/components/ConfirmModal';

export default function EditTenantPage() {
  const params = useParams();
  const tenantId = params.id as string;
  const { addToast } = useToast();

  // Tenant state
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [tenantLoading, setTenantLoading] = useState(true);
  const [tenantError, setTenantError] = useState<string | null>(null);
  const [isSavingTenant, setIsSavingTenant] = useState(false);

  // Active toggle state
  const [isToggleModalOpen, setIsToggleModalOpen] = useState(false);
  const [isTogglingActive, setIsTogglingActive] = useState(false);

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
          `/api/v1/admin/tenants/${tenantId}`
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
        `/api/v1/admin/tenants/${tenantId}`,
        data
      );
      setTenant(updated);
      addToast('success', 'Dados salvos');
    } catch (err) {
      let message = 'Erro ao salvar tenant';
      if (err instanceof ApiError) {
        if (err.status === 409) {
          message = 'Este slug já está sendo usado por outro tenant';
        } else {
          message = err.message;
        }
      }
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

  const handleToggleActive = async () => {
    if (!tenant) return;
    try {
      setIsTogglingActive(true);
      const newActiveState = !tenant.active;
      const updated = await apiPut<TenantResponse>(
        `/api/v1/admin/tenants/${tenantId}`,
        { active: newActiveState }
      );
      setTenant(updated);
      addToast('success', newActiveState ? 'Tenant reativado' : 'Tenant desativado');
      setIsToggleModalOpen(false);
    } catch (err) {
      let message = 'Erro ao alternar tenant';
      if (err instanceof ApiError) {
        message = err.message;
      }
      addToast('error', message);
    } finally {
      setIsTogglingActive(false);
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
            <div className="flex items-center justify-between gap-4 mb-4">
              <h2 className="text-xl font-semibold text-gray-900">
                Dados do Tenant
              </h2>
              <span
                className={`px-3 py-1 rounded-full text-sm font-medium ${
                  tenant.active
                    ? 'bg-green-100 text-green-800'
                    : 'bg-gray-200 text-gray-800'
                }`}
              >
                {tenant.active ? 'Ativo' : 'Inativo'}
              </span>
            </div>
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
            <div className="mt-4 border-t pt-4">
              <button
                onClick={() => setIsToggleModalOpen(true)}
                disabled={
                  isTogglingActive || getProtectedTenantReason(tenantId) !== null
                }
                title={getProtectedTenantReason(tenantId) || ''}
                className={`w-full px-4 py-2 rounded font-medium transition-colors ${
                  getProtectedTenantReason(tenantId)
                    ? 'bg-gray-300 text-gray-600 cursor-not-allowed opacity-50'
                    : tenant.active
                      ? 'bg-red-600 text-white hover:bg-red-700'
                      : 'bg-green-600 text-white hover:bg-green-700'
                }`}
              >
                {tenant.active ? 'Desativar tenant' : 'Reativar tenant'}
              </button>
            </div>
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

      {tenant && (
        <ConfirmModal
          isOpen={isToggleModalOpen}
          title={tenant.active ? 'Desativar tenant' : 'Reativar tenant'}
          message={
            tenant.active
              ? `Tem certeza que deseja desativar o tenant '${tenant.name}'? Usuários deste tenant não conseguirão mais fazer login no PDV.`
              : `Tem certeza que deseja reativar o tenant '${tenant.name}'? Usuários poderão fazer login novamente.`
          }
          confirmLabel={tenant.active ? 'Desativar' : 'Reativar'}
          isDangerous={tenant.active}
          isLoading={isTogglingActive}
          onConfirm={handleToggleActive}
          onCancel={() => setIsToggleModalOpen(false)}
        />
      )}
    </div>
  );
}
