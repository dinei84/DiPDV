'use client';

import React, { useMemo, useState } from 'react';
import { X } from 'lucide-react';
import { MoneyInput } from '@/components/MoneyInput';
import { apiPatch } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import { useCashRegister } from '@/lib/cash-register/CashRegisterContext';
import { useConfirm } from '@/lib/confirm';
import { apiPriceToCents, centsToApiString, centsToBRL } from '@/lib/price';
import { toast } from '@/lib/toast';
import { CashRegister, CloseCashRegisterDTO } from '@/lib/types';

interface CloseCashRegisterDialogProps {
  register: CashRegister;
  onClose: () => void;
  onClosed: () => void;
}

function calculateExpectedCents(register: CashRegister) {
  const supply = register.movements
    .filter((movement) => movement.type === 'SUPPLY')
    .reduce((total, movement) => total + apiPriceToCents(movement.amount), 0);
  const bleeding = register.movements
    .filter((movement) => movement.type === 'BLEEDING')
    .reduce((total, movement) => total + apiPriceToCents(movement.amount), 0);

  return apiPriceToCents(register.openingBalance)
    + apiPriceToCents(register.totalCash)
    + apiPriceToCents(register.totalPix)
    + supply
    - bleeding;
}

export default function CloseCashRegisterDialog({
  register,
  onClose,
  onClosed,
}: CloseCashRegisterDialogProps) {
  const confirm = useConfirm();
  const { refresh } = useCashRegister();
  const [physicalBalance, setPhysicalBalance] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const expectedCents = useMemo(() => calculateExpectedCents(register), [register]);
  const differenceCents = physicalBalance - expectedCents;

  const handleClose = async () => {
    const ok = await confirm({
      title: 'Fechar caixa',
      message: 'Esta ação é irreversível. Confirme apenas após conferir o valor físico do caixa.',
      danger: true,
    });

    if (!ok) return;

    setSubmitting(true);
    try {
      const payload: CloseCashRegisterDTO = {
        physicalBalance: centsToApiString(physicalBalance),
      };
      await apiPatch<CashRegister>(`/api/v1/cash-registers/${register.id}/close`, payload);
      toast.success('Caixa fechado com sucesso');
      await refresh();
      onClosed();
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        toast.error('Seu perfil não tem permissão para fechar caixa');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[9000] flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md overflow-hidden rounded-lg bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-gray-200 p-5">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Fechar caixa</h2>
            <p className="text-sm text-gray-500">Valor esperado: {centsToBRL(expectedCents)}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-gray-500 hover:bg-gray-100"
            aria-label="Fechar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="space-y-4 p-5">
          <MoneyInput
            label="Valor físico conferido"
            value={physicalBalance}
            onChange={setPhysicalBalance}
            disabled={submitting}
          />

          <div
            className={`rounded-md border px-3 py-2 text-sm font-medium ${
              differenceCents >= 0
                ? 'border-green-200 bg-green-50 text-green-700'
                : 'border-red-200 bg-red-50 text-red-700'
            }`}
          >
            Diferença: {centsToBRL(differenceCents)}
          </div>
        </div>

        <div className="flex justify-end gap-3 border-t border-gray-200 bg-gray-50 p-5">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:bg-gray-400"
          >
            Confirmar fechamento
          </button>
        </div>
      </div>
    </div>
  );
}
