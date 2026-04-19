import type { ReactNode } from "react";

const TONE_CLASSES = {
  neutral: "border-white/10 bg-white/5 text-slate-300",
  accent: "border-cyan-400/20 bg-cyan-400/10 text-cyan-200",
  success: "border-emerald-400/20 bg-emerald-400/10 text-emerald-200",
  warning: "border-amber-300/20 bg-amber-300/10 text-amber-100",
  danger: "border-rose-400/20 bg-rose-400/10 text-rose-200",
} as const;

type Tone = keyof typeof TONE_CLASSES;

interface StatusPillProps {
  label: ReactNode;
  tone?: Tone;
}

export default function StatusPill({
  label,
  tone = "neutral",
}: StatusPillProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-3 py-1 text-[0.68rem] font-semibold uppercase tracking-[0.22em] ${TONE_CLASSES[tone]}`}
    >
      {label}
    </span>
  );
}
