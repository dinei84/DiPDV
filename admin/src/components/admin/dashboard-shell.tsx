"use client";

import type { ReactNode } from "react";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { adminLogout } from "@/lib/api";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Visao geral", hint: "Panorama da plataforma" },
  { href: "/dashboard/tenants", label: "Clientes", hint: "Carteira e atividade" },
  { href: "/dashboard/engagement", label: "Engajamento", hint: "Risco e uso recente" },
];

function isCurrentPath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function DashboardShell({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  const pathname = usePathname();
  const [logoutError, setLogoutError] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    setLogoutError("");

    try {
      await adminLogout();
      window.location.assign("/login");
    } catch (error) {
      setLoggingOut(false);
      setLogoutError(
        error instanceof Error
          ? error.message
          : "Nao foi possivel encerrar a sessao do painel.",
      );
    }
  }

  return (
    <div className="min-h-screen lg:flex">
      <aside className="dashboard-grid border-b border-white/10 bg-[rgba(6,9,18,0.86)] lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-72 lg:flex-col lg:border-b-0 lg:border-r">
        <div className="space-y-5 px-5 py-6 lg:px-6">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.32em] text-cyan-200/75">
              DiPDV
            </p>
            <h1 className="mt-3 font-display text-3xl text-white">Admin room</h1>
            <p className="mt-3 max-w-xs text-sm leading-6 text-slate-400">
              Operacao central para monitorar tenants, receita consolidada e risco de churn.
            </p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
            <p className="text-[0.68rem] font-semibold uppercase tracking-[0.22em] text-slate-500">
              Controle atual
            </p>
            <p className="mt-3 text-sm text-white">Sessao SUPER_ADMIN ativa</p>
            <p className="mt-2 text-sm leading-6 text-slate-400">
              O painel usa redirecionamento otimista em `proxy.ts` e autenticacao por cookie.
            </p>
          </div>
        </div>

        <nav className="flex gap-3 overflow-x-auto px-5 pb-5 lg:flex-1 lg:flex-col lg:px-6">
          {NAV_ITEMS.map((item) => {
            const current = isCurrentPath(pathname, item.href);

            return (
              <Link
                className={`min-w-[13rem] rounded-3xl border px-4 py-3 transition lg:min-w-0 ${
                  current
                    ? "border-cyan-300/20 bg-cyan-300/10 text-white"
                    : "border-white/8 bg-white/[0.03] text-slate-300 hover:border-white/15 hover:bg-white/[0.06]"
                }`}
                href={item.href}
                key={item.href}
              >
                <p className="text-sm font-semibold">{item.label}</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">{item.hint}</p>
              </Link>
            );
          })}
        </nav>

        <div className="space-y-3 border-t border-white/10 px-5 py-5 lg:px-6">
          {logoutError ? (
            <p className="text-sm text-rose-200">{logoutError}</p>
          ) : null}

          <button
            className="inline-flex w-full items-center justify-center rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm font-semibold text-slate-200 transition hover:border-rose-300/30 hover:text-white disabled:opacity-60"
            disabled={loggingOut}
            onClick={handleLogout}
            type="button"
          >
            {loggingOut ? "Encerrando..." : "Sair do painel"}
          </button>
        </div>
      </aside>

      <main className="flex-1 px-4 py-4 sm:px-6 sm:py-6 lg:px-8 lg:py-8">
        {children}
      </main>
    </div>
  );
}
