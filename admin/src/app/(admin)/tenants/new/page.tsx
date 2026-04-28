'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { TenantRequest, TenantResponse } from '@/lib/types';
import { apiPost, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toast/useToast';
import TenantForm from '../_components/TenantForm';

export default function NewTenantPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (data: TenantRequest) => {
    try {
      setIsLoading(true);
      const response = await apiPost<TenantResponse>(
        '/admin/tenants',
        data
      );
      addToast('success', 'Tenant criado');
      router.push(`/tenants/${response.id}`);
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : 'Erro ao criar tenant';
      addToast('error', message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="max-w-2xl">
      <h1 className="text-3xl font-bold mb-6">Novo Tenant</h1>
      <div className="bg-white rounded-lg shadow p-6">
        <TenantForm
          onSubmit={handleSubmit}
          submitLabel="Criar"
          isLoading={isLoading}
        />
      </div>
    </div>
  );
}
