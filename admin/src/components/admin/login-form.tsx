"use client";

import { useState } from "react";
import { adminLogin } from "@/lib/api";

interface LoginFormProps {
  requestedPath?: string;
  session?: string;
}

function normalizeDestination(path?: string) {
  if (!path || !path.startsWith("/") || path.startsWith("//") || path.startsWith("/login")) {
    return "/dashboard";
  }

  return path;
}

export default function LoginForm({
  requestedPath,
  session,
}: LoginFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      await adminLogin(email, password);
      window.location.assign(normalizeDestination(requestedPath));
    } catch (submissionError) {
      setError(
        submissionError instanceof Error
          ? submissionError.message
          : "Nao foi possivel autenticar no painel.",
      );
      setSubmitting(false);
    }
  }

  return (
    <div className="mesh-surface relative w-full max-w-md overflow-hidden rounded-[2rem] p-8 sm:p-10">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-[var(--accent)] to-transparent" />

      <div className="mb-8">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-cyan-200/80">
          DiPDV Control Room
        </p>
        <h1 className="mt-4 font-display text-4xl leading-none text-white">
          Painel administrativo
        </h1>
        <p className="mt-4 text-sm leading-6 text-slate-400">
          Acesso exclusivo para SUPER_ADMIN com sessao baseada em cookie HttpOnly.
        </p>
      </div>

      {session === "expired" ? (
        <div className="mb-4 rounded-2xl border border-amber-300/20 bg-amber-300/10 px-4 py-3 text-sm text-amber-100">
          Sua sessao expirou. Entre novamente para continuar.
        </div>
      ) : null}

      {error ? (
        <div className="mb-4 rounded-2xl border border-rose-400/20 bg-rose-400/10 px-4 py-3 text-sm text-rose-100">
          {error}
        </div>
      ) : null}

      <form className="space-y-5" onSubmit={handleSubmit}>
        <label className="block">
          <span className="mb-2 block text-xs font-semibold uppercase tracking-[0.22em] text-slate-400">
            E-mail
          </span>
          <input
            autoComplete="email"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/50 focus:bg-white/8"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="superadmin@dipdv.app"
            required
            type="email"
            value={email}
          />
        </label>

        <label className="block">
          <span className="mb-2 block text-xs font-semibold uppercase tracking-[0.22em] text-slate-400">
            Senha
          </span>
          <input
            autoComplete="current-password"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/50 focus:bg-white/8"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Sua senha de super admin"
            required
            type="password"
            value={password}
          />
        </label>

        <button
          className="inline-flex w-full items-center justify-center rounded-2xl bg-[linear-gradient(135deg,var(--accent),var(--accent-strong))] px-4 py-3 text-sm font-semibold text-slate-950 transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={submitting}
          type="submit"
        >
          {submitting ? "Entrando..." : "Entrar no painel"}
        </button>
      </form>
    </div>
  );
}
