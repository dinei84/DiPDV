import { useEffect, useState } from 'react';
import {
  ModuleCatalogItem,
  TenantModuleStatus,
} from './types';
import { apiGet, ApiError } from './api';

export interface UseTenantModulesResult {
  modules: (TenantModuleStatus & { name: string; description: string; tier: 'BASE' | 'PAID' })[];
  loading: boolean;
  error: string | null;
}

export function useTenantModules(
  tenantId: string
): UseTenantModulesResult {
  const [modules, setModules] = useState<
    (TenantModuleStatus & { name: string; description: string; tier: 'BASE' | 'PAID' })[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const fetchModules = async () => {
      try {
        setLoading(true);
        setError(null);

        // Fetch catalog
        const catalog = await apiGet<ModuleCatalogItem[]>(
          '/api/v1/admin/modules/catalog'
        );

        // Fetch enabled codes for tenant
        const enabledCodes = await apiGet<string[]>(
          `/api/v1/admin/modules/tenants/${tenantId}`
        );

        if (isMounted) {
          // Cross catalog with enabled codes
          const modulesWithStatus = catalog.map((item) => ({
            moduleCode: item.code,
            name: item.name,
            description: item.description,
            tier: item.tier,
            enabled: enabledCodes.includes(item.code),
            enabledAt: '',
            enabledBy: undefined,
          }));

          setModules(modulesWithStatus);
        }
      } catch (err) {
        if (isMounted) {
          const message =
            err instanceof ApiError
              ? err.message
              : 'Erro ao carregar módulos';
          setError(message);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    fetchModules();

    return () => {
      isMounted = false;
    };
  }, [tenantId]);

  return { modules, loading, error };
}
