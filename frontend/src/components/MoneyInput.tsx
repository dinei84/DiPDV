'use client';

import React, { useState, useEffect } from 'react';

interface MoneyInputProps {
  value: number; // value in cents
  onChange: (cents: number) => void;
  label?: string;
  disabled?: boolean;
  className?: string;
}

export const MoneyInput: React.FC<MoneyInputProps> = ({
  value,
  onChange,
  label,
  disabled,
  className,
}) => {
  const [displayValue, setDisplayValue] = useState('');

  const formatCentsToBRL = (cents: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(cents / 100);
  };

  useEffect(() => {
    setDisplayValue(formatCentsToBRL(value));
  }, [value]);

  const handleFocus = () => {
    if (value === 0) {
      setDisplayValue('');
    } else {
      setDisplayValue((value / 100).toString().replace('.', ','));
    }
  };

  const handleBlur = () => {
    setDisplayValue(formatCentsToBRL(value));
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    const cents = parseInt(raw || '0', 10);
    onChange(cents);
  };

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      {label && <label className="text-sm font-medium text-gray-700">{label}</label>}
      <input
        type="text"
        inputMode="numeric"
        value={displayValue}
        onChange={handleChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        disabled={disabled}
        placeholder="R$ 0,00"
        className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm disabled:bg-gray-50 disabled:text-gray-500 p-2 border"
      />
    </div>
  );
};
