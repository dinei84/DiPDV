'use client';

import { useState } from 'react';
import { apiPost, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast/useToast';

interface ModuleToggleProps {
  code: string;
  name: string;
  tier: 'BASE' | 'PAID';
  enabled: boolean;
  tenantId: string;
  onToggle?: (code: string, newState: boolean) => void;
}

export default function ModuleToggle({
  code,
  name,
  tier,
  enabled,
  tenantId,
  onToggle,
}: ModuleToggleProps) {
  const { addToast } = useToast();
  const [isOptimistic, setIsOptimistic] = useState(enabled);
  const [isPending, setIsPending] = useState(false);

  const isDisabled = tier === 'BASE';

  const handleToggle = async () => {
    if (isDisabled) return;

    const previousState = isOptimistic;
    setIsOptimistic(!isOptimistic);
    setIsPending(true);

    try {
      const action = isOptimistic ? 'disable' : 'enable';
      await apiPost(
        `/admin/modules/tenants/${tenantId}/${action}`,
        { code }
      );
      if (onToggle) {
        onToggle(code, !previousState);
      }
    } catch (err) {
      setIsOptimistic(previousState);
      const message =
        err instanceof ApiError
          ? err.message
          : 'Erro ao atualizar módulo';
      addToast('error', message);
    } finally {
      setIsPending(false);
    }
  };

  return (
    <div className="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
      <div className="flex-1">
        <h3 className="font-medium text-gray-900">{name}</h3>
        <div className="flex items-center gap-2 mt-1">
          <span className="text-xs font-semibold px-2 py-1 bg-gray-100 text-gray-700 rounded">
            {tier}
          </span>
          {isDisabled && (
            <span
              className="text-xs text-gray-500 cursor-help"
              title="Módulo essencial, sempre ativo"
            >
              Essencial
            </span>
          )}
        </div>
      </div>

      <button
        onClick={handleToggle}
        disabled={isDisabled || isPending}
        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
          isOptimistic
            ? 'bg-green-600'
            : 'bg-gray-300'
        } ${
          (isDisabled || isPending)
            ? 'opacity-50 cursor-not-allowed'
            : 'cursor-pointer'
        }`}
        title={isDisabled ? 'Módulo essencial, sempre ativo' : ''}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
            isOptimistic ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </button>
    </div>
  );
}
