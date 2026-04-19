"use client";

import { useEffect, useState } from "react";
import ErrorPanel from "@/components/admin/error-panel";
import LoadingPanel from "@/components/admin/loading-panel";
import StatusPill from "@/components/admin/status-pill";
import { adminFetch } from "@/lib/api";
import { formatCurrency, formatInteger } from "@/lib/format";
import type { GlobalStats } from "@/lib/types";

export default function DashboardPage() {
  const [stats, setStats] = useState<GlobalStats | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminFetch<GlobalStats>("/api/v1/admin/dashboard/stats")
      .then(setStats)
      .catch((requestError) => {
        setError(
          requestError instanceof Error
            ? requestError.message
            : "Nao foi possivel obter os indicadores globais.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <LoadingPanel label="Buscando consolidado global da plataforma..." />;
  }

  if (error) {
    return <ErrorPanel message={error} />;
  }

  if (!stats) {
    return <ErrorPanel message="A API nao retornou dados para o dashboard global." />;
  }

  const cards = [
    {
      label: "Base total",
      value: formatInteger(stats.tenantCount),
      tone: "accent" as const,
      detail: "Tenants cadastrados no ecossistema",
    },
    {
      label: "Operacao ativa",
      value: formatInteger(stats.activeTenantCount),
      tone: "success" as const,
      detail: "Clientes com operacao ativa",
    },
    {
      label: "Pedidos",
      value: formatInteger(stats.totalOrders),
      tone: "neutral" as const,
      detail: "Total agregado de pedidos",
    },
    {
      label: "Receita",
      value: formatCurrency(stats.totalRevenue),
      tone: "warning" as const,
      detail: "Faturamento consolidado da plataforma",
    },
  ];

  return (
    <section className="space-y-6">
      <header className="mesh-surface rounded-[2rem] p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-cyan-200/80">
              Visao geral
            </p>
            <h2 className="mt-4 font-display text-4xl text-white">Plataforma em panorama</h2>
            <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-400">
              Leitura consolidada da operacao com foco em tamanho da base, atividade atual
              e tenants com maior tracao nos ultimos 30 dias.
            </p>
          </div>
          <StatusPill label="Atualizacao em tempo real" tone="accent" />
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <article className="mesh-surface rounded-[1.75rem] p-5" key={card.label}>
            <StatusPill label={card.label} tone={card.tone} />
            <p className="mt-5 font-display text-4xl leading-none text-white">{card.value}</p>
            <p className="mt-3 text-sm leading-6 text-slate-400">{card.detail}</p>
          </article>
        ))}
      </div>

      <section className="mesh-surface overflow-hidden rounded-[2rem]">
        <div className="flex flex-col gap-3 border-b border-white/10 px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">
              Ranking de receita
            </p>
            <h3 className="mt-2 font-display text-2xl text-white">
              Top tenants dos ultimos 30 dias
            </h3>
          </div>
          <StatusPill label={`${stats.topTenants.length} clientes destacados`} tone="neutral" />
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-[0.2em] text-slate-500">
              <tr>
                <th className="px-6 py-4 font-medium">Cliente</th>
                <th className="px-6 py-4 font-medium">Plano</th>
                <th className="px-6 py-4 text-right font-medium">Pedidos</th>
                <th className="px-6 py-4 text-right font-medium">Receita</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/6">
              {stats.topTenants.length > 0 ? (
                stats.topTenants.map((tenant) => (
                  <tr className="transition hover:bg-white/[0.03]" key={tenant.id}>
                    <td className="px-6 py-4">
                      <p className="font-semibold text-white">{tenant.name}</p>
                      <p className="mt-1 text-xs uppercase tracking-[0.16em] text-slate-500">
                        Tenant ID {tenant.id.slice(0, 8)}
                      </p>
                    </td>
                    <td className="px-6 py-4">
                      <StatusPill label={tenant.planType} tone="neutral" />
                    </td>
                    <td className="px-6 py-4 text-right text-slate-200">
                      {formatInteger(tenant.orders30d)}
                    </td>
                    <td className="px-6 py-4 text-right font-semibold text-white">
                      {formatCurrency(tenant.revenue30d)}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="px-6 py-8 text-center text-sm text-slate-500" colSpan={4}>
                    Nenhum tenant acumulou movimento no recorte atual.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
