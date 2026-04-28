import { getAuth } from '@/lib/auth';

export default function DashboardPage() {
  const auth = getAuth();

  if (!auth) {
    return null;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-600 mt-2">Bem-vindo ao painel de administração</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Informações do Usuário
          </h2>
          <dl className="space-y-4">
            <div>
              <dt className="text-sm font-medium text-gray-500">Email</dt>
              <dd className="text-gray-900">{auth.name}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Função</dt>
              <dd className="text-gray-900">{auth.role}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">ID do Tenant</dt>
              <dd className="text-gray-900 font-mono text-sm">{auth.tenantId}</dd>
            </div>
          </dl>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Gerenciamento
          </h2>
          <p className="text-gray-600">
            Acesse os menus laterais para gerenciar tenants e módulos
          </p>
        </div>
      </div>
    </div>
  );
}
