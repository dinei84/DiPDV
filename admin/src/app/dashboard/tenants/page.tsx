"use client";

import { useEffect, useState } from "react";
import ErrorPanel from "@/components/admin/error-panel";
import LoadingPanel from "@/components/admin/loading-panel";
import StatusPill from "@/components/admin/status-pill";
import { adminFetch } from "@/lib/api";
import { formatDateTime, formatRelativeActivity, formatInteger } from "@/lib/format";
import type { TenantSummary } from "@/lib/types";

const PLAN_TONES = {
  TRIAL: "warning",
  ACTIVE: "success",
  SUSPENDED: "danger",
} as const;

export default function TenantsPage() {
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminFetch<TenantSummary[]>("/api/v1/admin/tenants")
      .then(setTenants)
      .catch((requestError) => {
        setError(
          requestError instanceof Error
            ? requestError.message
            : "Nao foi possivel carregar a carteira de clientes.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  const activeCount = tenants.filter((tenant) => tenant.active).length;

  if (loading) {
    return <LoadingPanel label="Buscando lista consolidada de tenants..." />;
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
              Clientes
            </p>
            <h2 className="mt-4 font-display text-4xl text-white">Carteira de tenants</h2>
            <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-400">
              Visao operacional da base com owner, plano, usuarios e ultima atividade.
              A criacao e o detalhe profundo do tenant permanecem fora do escopo desta sprint.
            </p>
          </div>

          <div className="flex flex-wrap gap-3">
            <StatusPill label={`${activeCount} ativos`} tone="success" />
            <StatusPill label={`${tenants.length - activeCount} inativos`} tone="danger" />
          </div>
        </div>
      </header>

      <section className="mesh-surface overflow-hidden rounded-[2rem]">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-[0.2em] text-slate-500">
              <tr>
                <th className="px-6 py-4 font-medium">Cliente</th>
                <th className="px-6 py-4 font-medium">Owner</th>
                <th className="px-6 py-4 font-medium">Plano</th>
                <th className="px-6 py-4 text-right font-medium">Usuarios</th>
                <th className="px-6 py-4 font-medium">Ultima atividade</th>
                <th className="px-6 py-4 font-medium">Criado em</th>
                <th className="px-6 py-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/6">
              {tenants.length > 0 ? (
                tenants.map((tenant) => (
                  <tr className="transition hover:bg-white/[0.03]" key={tenant.id}>
                    <td className="px-6 py-4">
                      <p className="font-semibold text-white">{tenant.name}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        {tenant.slug ?? "slug pendente"}
                      </p>
                    </td>
                    <td className="px-6 py-4 text-slate-300">{tenant.ownerEmail}</td>
                    <td className="px-6 py-4">
                      <StatusPill
                        label={tenant.planType}
                        tone={PLAN_TONES[tenant.planType as keyof typeof PLAN_TONES] ?? "neutral"}
                      />
                    </td>
                    <td className="px-6 py-4 text-right text-slate-200">
                      {formatInteger(tenant.userCount)}
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-white">{formatRelativeActivity(tenant.lastActivityAt)}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        {tenant.lastActivityAt ? formatDateTime(tenant.lastActivityAt) : "Sem uso recente"}
                      </p>
                    </td>
                    <td className="px-6 py-4 text-slate-300">
                      {formatDateTime(tenant.createdAt)}
                    </td>
                    <td className="px-6 py-4">
                      <StatusPill
                        label={tenant.active ? "Ativo" : "Inativo"}
                        tone={tenant.active ? "success" : "danger"}
                      />
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="px-6 py-8 text-center text-sm text-slate-500" colSpan={7}>
                    Nenhum tenant disponivel para exibicao.
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
