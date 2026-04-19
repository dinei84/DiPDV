interface ErrorPanelProps {
  message: string;
}

export default function ErrorPanel({ message }: ErrorPanelProps) {
  return (
    <div className="mesh-surface border-rose-400/20 p-6">
      <p className="text-sm font-semibold uppercase tracking-[0.24em] text-rose-300">
        Falha ao carregar
      </p>
      <p className="mt-3 text-sm text-rose-100/85">{message}</p>
    </div>
  );
}
