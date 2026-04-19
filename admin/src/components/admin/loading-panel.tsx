interface LoadingPanelProps {
  label?: string;
}

export default function LoadingPanel({
  label = "Carregando painel administrativo...",
}: LoadingPanelProps) {
  return (
    <div className="mesh-surface grid min-h-[280px] place-items-center p-8">
      <div className="flex flex-col items-center gap-4 text-center">
        <span className="h-10 w-10 animate-spin rounded-full border-2 border-white/15 border-t-[var(--accent)]" />
        <p className="max-w-sm text-sm text-slate-400">{label}</p>
      </div>
    </div>
  );
}
