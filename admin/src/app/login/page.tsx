import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import LoginForm from "@/components/admin/login-form";

type SearchParams = Promise<{
  session?: string | string[];
  from?: string | string[];
}>;

function readParam(value?: string | string[]) {
  return Array.isArray(value) ? value[0] : value;
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const cookieStore = await cookies();

  if (cookieStore.get("dipdv_admin_token")?.value) {
    redirect("/dashboard");
  }

  const params = await searchParams;
  const session = readParam(params.session);
  const requestedPath = readParam(params.from);

  return (
    <main className="relative min-h-screen overflow-hidden px-4 py-10 sm:px-6 lg:px-8">
      <div className="absolute left-[-12rem] top-16 h-64 w-64 rounded-full bg-[rgba(54,201,198,0.18)] blur-3xl" />
      <div className="absolute bottom-0 right-[-10rem] h-72 w-72 rounded-full bg-[rgba(246,189,96,0.14)] blur-3xl" />

      <div className="relative mx-auto flex min-h-[calc(100vh-5rem)] max-w-6xl flex-col justify-center gap-10 lg:flex-row lg:items-center lg:gap-16">
        <section className="max-w-xl">
          <p className="text-xs font-semibold uppercase tracking-[0.32em] text-cyan-200/80">
            Sprint 4
          </p>
          <h2 className="mt-6 font-display text-5xl leading-none text-white sm:text-6xl">
            Controle central da base DiPDV.
          </h2>
          <p className="mt-6 text-base leading-8 text-slate-300">
            Monitore faturamento global, acompanhe risco de churn e identifique
            tenants com atividade fora do esperado em uma interface dedicada ao
            SUPER_ADMIN.
          </p>

          <div className="mt-8 grid gap-4 sm:grid-cols-3">
            <div className="mesh-surface rounded-[1.5rem] p-4">
              <p className="text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-slate-500">
                Autenticacao
              </p>
              <p className="mt-3 text-sm text-white">Cookie HttpOnly</p>
            </div>
            <div className="mesh-surface rounded-[1.5rem] p-4">
              <p className="text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-slate-500">
                Camada
              </p>
              <p className="mt-3 text-sm text-white">Proxy no App Router</p>
            </div>
            <div className="mesh-surface rounded-[1.5rem] p-4">
              <p className="text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-slate-500">
                Dados
              </p>
              <p className="mt-3 text-sm text-white">Stats, tenants e risco</p>
            </div>
          </div>
        </section>

        <LoginForm requestedPath={requestedPath} session={session} />
      </div>
    </main>
  );
}
