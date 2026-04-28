'use client';

import { useMemo } from 'react';
import { TenantModuleStatus } from '@/lib/types';
import ModuleToggle from './ModuleToggle';

interface ModulesListProps {
  modules: (TenantModuleStatus & {
    name: string;
    description: string;
    tier: 'BASE' | 'PAID';
  })[];
  tenantId: string;
  onModuleToggle?: (code: string, newState: boolean) => void;
}

export default function ModulesList({
  modules,
  tenantId,
  onModuleToggle,
}: ModulesListProps) {
  const { baseModules, paidModules } = useMemo(() => {
    return {
      baseModules: modules.filter((m) => m.tier === 'BASE'),
      paidModules: modules.filter((m) => m.tier === 'PAID'),
    };
  }, [modules]);

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Módulos Essenciais
        </h3>
        <div className="space-y-3">
          {baseModules.length === 0 ? (
            <p className="text-sm text-gray-500">
              Nenhum módulo essencial disponível.
            </p>
          ) : (
            baseModules.map((module) => (
              <ModuleToggle
                key={module.moduleCode}
                code={module.moduleCode}
                name={module.name}
                tier={module.tier}
                enabled={module.enabled}
                tenantId={tenantId}
                onToggle={onModuleToggle}
              />
            ))
          )}
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          Módulos Adicionais
        </h3>
        <div className="space-y-3">
          {paidModules.length === 0 ? (
            <p className="text-sm text-gray-500">
              Nenhum módulo adicional disponível.
            </p>
          ) : (
            paidModules.map((module) => (
              <ModuleToggle
                key={module.moduleCode}
                code={module.moduleCode}
                name={module.name}
                tier={module.tier}
                enabled={module.enabled}
                tenantId={tenantId}
                onToggle={onModuleToggle}
              />
            ))
          )}
        </div>
      </div>
    </div>
  );
}
