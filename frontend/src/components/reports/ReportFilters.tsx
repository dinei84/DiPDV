'use client';

interface Props {
  from: string;
  to: string;
  onFromChange: (v: string) => void;
  onToChange: (v: string) => void;
  onApply: () => void;
}

export default function ReportFilters({
  from,
  to,
  onFromChange,
  onToChange,
  onApply,
}: Props) {
  return (
    <div className="flex gap-3 items-end mb-6">
      <div>
        <label className="block text-xs text-gray-500 mb-1">De</label>
        <input
          type="date"
          value={from}
          onChange={(e) => onFromChange(e.target.value)}
          className="border rounded px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">Até</label>
        <input
          type="date"
          value={to}
          onChange={(e) => onToChange(e.target.value)}
          className="border rounded px-3 py-2 text-sm"
        />
      </div>
      <button
        onClick={onApply}
        className="bg-gray-800 text-white px-4 py-2 rounded text-sm hover:bg-gray-700"
      >
        Aplicar
      </button>
    </div>
  );
}
