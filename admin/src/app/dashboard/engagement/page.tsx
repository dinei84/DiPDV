"use client";

import { useEffect, useState } from "react";
import ErrorPanel from "@/components/admin/error-panel";
import LoadingPanel from "@/components/admin/loading-panel";
import StatusPill from "@/components/admin/status-pill";
import { adminFetch } from "@/lib/api";
import { formatCurrency, formatRelativeActivity, formatInteger } from "@/lib/format";
import type { EngagementMetric } from "@/lib/types";

const STATUS_CONFIG = {
  ALL: { label: "Todos", tone: "neutral" as const },
  ACTIVE: { label: "Ativo", tone: "success" as const },
  AT_RISK: { label: "Em risco", tone: "warning" as const },
  INACTIVE: { label: "Inativo", tone: "danger" as const },
  NEVER: { label: "Nunca usou", tone: "neutral" as const },
};

type FilterKey = keyof typeof STATUS_CONFIG;

export default function EngagementPage() {
  const [metrics, setMetrics] = useState<EngagementMetric[]>([]);
  const [filter, setFilter] = useState<FilterKey>("ALL");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminFetch<EngagementMetric[]>("/api/v1/admin/dashboard/engagement")
      .then(setMetrics)
      .catch((requestError) => {
        setError(
          requestError instanceof Error
            ? requestError.message
            : "Nao foi possivel carregar o health check de engajamento.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  const filteredMetrics =
    filter === "ALL"
      ? metrics
      : metrics.filter((metric) => metric.engagementStatus === filter);

  if (loading) {
    return <LoadingPanel label="Calculando status de engajamento por tenant..." />;
  }

  if (error) {
    return <ErrorPanel message={error} />;
  }

  return (
    <section className="space-y-6">
      <header className="mesh-surface rounded-[2rem] p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-cyan-200/80">
              Engajamento
            </p>
            <h2 className="mt-4 font-display text-4xl text-white">
              Sinais de uso dos ultimos 30 dias
            </h2>
            <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-400">
              Filtro rapido para localizar tenants ativos, em risco, inativos ou que nunca
              usaram a plataforma apos o onboarding.
            </p>
          </div>
          <StatusPill label={`${metrics.length} tenants avaliados`} tone="accent" />
        </div>
      </header>

      <div className="flex flex-wrap gap-3">
        {(Object.keys(STATUS_CONFIG) as FilterKey[]).map((status) => {
          const count =
            status === "ALL"
              ? metrics.length
              : metrics.filter((metric) => metric.engagementStatus === status).length;
          const current = filter === status;

          return (
            <button
              className={`rounded-full border px-4 py-2 text-sm font-semibold transition ${
                current
                  ? "border-cyan-300/20 bg-cyan-300/10 text-white"
                  : "border-white/10 bg-white/5 text-slate-300 hover:border-white/20 hover:text-white"
              }`}
              key={status}
              onClick={() => setFilter(status)}
              type="button"
            >
              {STATUS_CONFIG[status].label} <span className="text-slate-500">({count})</span>
            </button>
          );
        })}
      </div>

      <div className="space-y-4">
        {filteredMetrics.length > 0 ? (
          filteredMetrics.map((metric) => {
            const config =
              STATUS_CONFIG[metric.engagementStatus as Exclude<FilterKey, "ALL">] ??
              STATUS_CONFIG.NEVER;

            return (
              <article
                className="mesh-surface flex flex-col gap-5 rounded-[1.75rem] p-5 lg:flex-row lg:items-center lg:justify-between"
                key={metric.id}
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-3">
                    <h3 className="font-display text-2xl text-white">{metric.name}</h3>
                    <StatusPill label={metric.planType} tone="neutral" />
                    <StatusPill label={config.label} tone={config.tone} />
                  </div>

                  <p className="mt-3 text-sm leading-6 text-slate-400">
                    Ultima atividade: {formatRelativeActivity(metric.lastActivityAt)}
                  </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-3 lg:min-w-[29rem]">
                  <div className="rounded-[1.35rem] border border-white/8 bg-white/[0.03] p-4">
                    <p className="text-[0.68rem] font-semibold uppercase tracking-[0.2em] text-slate-500">
                      Pedidos 30d
                    </p>
                    <p className="mt-3 text-xl font-semibold text-white">
                      {formatInteger(metric.orders30d)}
                    </p>
                  </div>

                  <div className="rounded-[1.35rem] border border-white/8 bg-white/[0.03] p-4">
                    <p className="text-[0.68rem] font-semibold uppercase tracking-[0.2em] text-slate-500">
                      Receita 30d
                    </p>
                    <p className="mt-3 text-xl font-semibold text-white">
                      {formatCurrency(metric.revenue30d)}
                    </p>
                  </div>

                  <div className="rounded-[1.35rem] border border-white/8 bg-white/[0.03] p-4">
                    <p className="text-[0.68rem] font-semibold uppercase tracking-[0.2em] text-slate-500">
                      Categoria
                    </p>
                    <p className="mt-3 text-xl font-semibold text-white">{config.label}</p>
                  </div>
                </div>
              </article>
            );
          })
        ) : (
          <div className="mesh-surface rounded-[2rem] p-8 text-center text-sm text-slate-400">
            Nenhum tenant corresponde ao filtro selecionado.
          </div>
        )}
      </div>
    </section>
  );
}
