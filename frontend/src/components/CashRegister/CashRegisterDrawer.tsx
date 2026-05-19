'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { ArrowDownToLine, ArrowUpFromLine, X } from 'lucide-react';
import { MoneyInput } from '@/components/MoneyInput';
import { apiGet, apiPost } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import { getAuth } from '@/lib/auth';
import { useCashRegister } from '@/lib/cash-register/CashRegisterContext';
import { apiPriceToCents, apiPriceToBRL, centsToApiString, centsToBRL } from '@/lib/price';
import { toast } from '@/lib/toast';
import {
  CashMovementType,
  CashMovementDTO,
  CashRegister,
  OpenCashRegisterDTO,
} from '@/lib/types';
import CloseCashRegisterDialog from './CloseCashRegisterDialog';

interface CashRegisterDrawerProps {
  open: boolean;
  onClose: () => void;
}

type MovementFormState = Record<CashMovementType, { amount: number; description: string; error: string }>;

function formatOpenDuration(openedAt: string) {
  const totalMinutes = Math.max(0, Math.floor((Date.now() - new Date(openedAt).getTime()) / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours}h ${minutes}min`;
}

const initialMovementState: MovementFormState = {
  BLEEDING: { amount: 0, description: '', error: '' },
  SUPPLY: { amount: 0, description: '', error: '' },
};

export default function CashRegisterDrawer({ open, onClose }: CashRegisterDrawerProps) {
  const { currentCashRegister, refresh } = useCashRegister();
  const [details, setDetails] = useState<CashRegister | null>(null);
  const [openingBalance, setOpeningBalance] = useState(0);
  const [openingError, setOpeningError] = useState('');
  const [movementForm, setMovementForm] = useState<MovementFormState>(initialMovementState);
  const [submitting, setSubmitting] = useState(false);
  const [closeDialogOpen, setCloseDialogOpen] = useState(false);

  const auth = getAuth();
  const canClose = auth?.role === 'ADMIN' || auth?.role === 'MANAGER';
  const register = details ?? currentCashRegister;

  const movementTotals = useMemo(() => {
    const movements = register?.movements ?? [];
    return {
      supply: movements
        .filter((movement) => movement.type === 'SUPPLY')
        .reduce((total, movement) => total + apiPriceToCents(movement.amount), 0),
      bleeding: movements
        .filter((movement) => movement.type === 'BLEEDING')
        .reduce((total, movement) => total + apiPriceToCents(movement.amount), 0),
    };
  }, [register]);

  const loadDetails = async (cashRegister: CashRegister) => {
    if (auth?.role === 'CASHIER') {
      setDetails(cashRegister);
      return;
    }

    try {
      const data = await apiGet<CashRegister>(`/api/v1/cash-registers/${cashRegister.id}`);
      setDetails(data);
    } catch (error) {
      setDetails(cashRegister);
    }
  };

  useEffect(() => {
    if (!open || !currentCashRegister) {
      setDetails(null);
      return;
    }

    loadDetails(currentCashRegister);
  }, [open, currentCashRegister?.id]);

  if (!open) return null;

  const handleOpenRegister = async () => {
    if (openingBalance < 0) {
      setOpeningError('Fundo de caixa não pode ser negativo');
      return;
    }

    setSubmitting(true);
    setOpeningError('');
    try {
      const payload: OpenCashRegisterDTO = {
        openingBalance: centsToApiString(openingBalance),
      };
      await apiPost<CashRegister>('/api/v1/cash-registers', payload);
      toast.success('Caixa aberto com sucesso');
      await refresh();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(error.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const updateMovement = (
    type: CashMovementType,
    patch: Partial<MovementFormState[CashMovementType]>
  ) => {
    setMovementForm((current) => ({
      ...current,
      [type]: { ...current[type], ...patch },
    }));
  };

  const handleMovement = async (type: CashMovementType) => {
    if (!register) return;

    const form = movementForm[type];
    const description = form.description.trim();

    if (form.amount <= 0) {
      updateMovement(type, { error: 'Valor deve ser maior que zero' });
      return;
    }

    if (!description) {
      updateMovement(type, { error: 'Descrição é obrigatória' });
      return;
    }

    setSubmitting(true);
    updateMovement(type, { error: '' });
    try {
      const payload: CashMovementDTO = {
        type,
        amount: centsToApiString(form.amount),
        description: description.slice(0, 255),
      };
      await apiPost(`/api/v1/cash-registers/${register.id}/movements`, payload);
      toast.success(type === 'BLEEDING' ? 'Sangria registrada com sucesso' : 'Suprimento registrado com sucesso');
      updateMovement(type, { amount: 0, description: '', error: '' });
      await refresh();
      await loadDetails(register);
    } catch (error) {
      // apiFetch shows the backend message.
    } finally {
      setSubmitting(false);
    }
  };

  const renderMovementSection = (
    type: CashMovementType,
    title: string,
    buttonLabel: string,
    Icon: typeof ArrowDownToLine
  ) => (
    <section className="rounded-lg border border-gray-200 p-4">
      <div className="mb-3 flex items-center gap-2">
        <Icon className="h-4 w-4 text-gray-500" />
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
      </div>
      <div className="space-y-3">
        <MoneyInput
          label="Valor"
          value={movementForm[type].amount}
          onChange={(amount) => updateMovement(type, { amount, error: '' })}
          disabled={submitting}
        />
        <textarea
          maxLength={255}
          value={movementForm[type].description}
          onChange={(event) => updateMovement(type, { description: event.target.value, error: '' })}
          disabled={submitting}
          placeholder="Descrição"
          className="min-h-20 w-full rounded-md border border-gray-300 p-2 text-sm text-gray-900 shadow-sm focus:border-blue-500 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500"
        />
        {movementForm[type].error && (
          <p className="text-sm text-red-600">{movementForm[type].error}</p>
        )}
        <button
          type="button"
          onClick={() => handleMovement(type)}
          disabled={submitting}
          className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:bg-gray-400"
        >
          {buttonLabel}
        </button>
      </div>
    </section>
  );

  return (
    <div className="fixed inset-0 z-[8000]">
      <button
        type="button"
        aria-label="Fechar painel do caixa"
        onClick={onClose}
        className="absolute inset-0 bg-black/40"
      />

      <aside className="absolute right-0 top-0 flex h-full w-full max-w-xl flex-col bg-white shadow-2xl">
        <header className="flex items-center justify-between border-b border-gray-200 p-5">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Caixa</h2>
            <p className="text-sm text-gray-500">
              {register ? `Aberto há ${formatOpenDuration(register.openedAt)}` : 'Nenhum caixa aberto'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-gray-500 hover:bg-gray-100"
            aria-label="Fechar"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        {!register ? (
          <div className="flex-1 space-y-5 overflow-y-auto p-5">
            <MoneyInput
              label="Fundo de caixa"
              value={openingBalance}
              onChange={(value) => {
                setOpeningBalance(value);
                setOpeningError('');
              }}
              disabled={submitting}
            />
            {openingError && <p className="text-sm text-red-600">{openingError}</p>}
            <button
              type="button"
              onClick={handleOpenRegister}
              disabled={submitting}
              className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
            >
              Abrir caixa
            </button>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto p-5">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg border border-gray-200 p-3">
                  <p className="text-gray-500">Fundo</p>
                  <p className="font-semibold text-gray-900">{apiPriceToBRL(register.openingBalance)}</p>
                </div>
                <div className="rounded-lg border border-gray-200 p-3">
                  <p className="text-gray-500">Total CASH</p>
                  <p className="font-semibold text-gray-900">{apiPriceToBRL(register.totalCash)}</p>
                </div>
                <div className="rounded-lg border border-gray-200 p-3">
                  <p className="text-gray-500">Total PIX</p>
                  <p className="font-semibold text-gray-900">{apiPriceToBRL(register.totalPix)}</p>
                </div>
                <div className="rounded-lg border border-gray-200 p-3">
                  <p className="text-gray-500">Tempo aberto</p>
                  <p className="font-semibold text-gray-900">{formatOpenDuration(register.openedAt)}</p>
                </div>
              </div>

              <div className="mt-5 grid gap-4">
                {renderMovementSection('BLEEDING', 'Sangria', 'Registrar sangria', ArrowDownToLine)}
                {renderMovementSection('SUPPLY', 'Suprimento', 'Registrar suprimento', ArrowUpFromLine)}
              </div>

              <section className="mt-5">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-gray-900">Movimentos do caixa atual</h3>
                  <p className="text-xs text-gray-500">
                    +{centsToBRL(movementTotals.supply)} / -{centsToBRL(movementTotals.bleeding)}
                  </p>
                </div>
                <div className="overflow-hidden rounded-lg border border-gray-200">
                  {(register.movements ?? []).length === 0 ? (
                    <p className="p-4 text-sm text-gray-500">Nenhum movimento registrado</p>
                  ) : (
                    <div className="divide-y divide-gray-200">
                      {register.movements.map((movement) => (
                        <div key={movement.id} className="p-3 text-sm">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="font-medium text-gray-900">
                                {movement.type === 'BLEEDING' ? 'Sangria' : 'Suprimento'}
                              </p>
                              <p className="text-gray-500">{movement.description}</p>
                            </div>
                            <p className={movement.type === 'SUPPLY' ? 'text-green-700' : 'text-red-700'}>
                              {movement.type === 'SUPPLY' ? '+' : '-'}
                              {apiPriceToBRL(movement.amount)}
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </section>
            </div>

            {canClose && (
              <footer className="border-t border-gray-200 p-5">
                <button
                  type="button"
                  onClick={() => setCloseDialogOpen(true)}
                  className="w-full rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
                >
                  Fechar caixa
                </button>
              </footer>
            )}
          </>
        )}
      </aside>

      {register && closeDialogOpen && (
        <CloseCashRegisterDialog
          register={register}
          onClose={() => setCloseDialogOpen(false)}
          onClosed={() => {
            setCloseDialogOpen(false);
            setDetails(null);
          }}
        />
      )}
    </div>
  );
}
