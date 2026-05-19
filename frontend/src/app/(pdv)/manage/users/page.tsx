'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { ChevronRight, Eye, EyeOff, Plus, RotateCcw, Trash2, X } from 'lucide-react';
import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/lib/api';
import { getAuth } from '@/lib/auth';
import { Page, User, UserDTO } from '@/lib/types';
import { useConfirm } from '@/lib/confirm';
import { toast } from '@/lib/toast';

const ROLES: UserDTO['role'][] = ['MANAGER', 'CASHIER'];

export default function UsersPage() {
  const router = useRouter();
  const confirm = useConfirm();
  const auth = getAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState<UserDTO>({
    email: '',
    name: '',
    role: 'CASHIER',
    password: '',
  });

  useEffect(() => {
    const currentAuth = getAuth();
    if (currentAuth?.role !== 'ADMIN') {
      router.replace('/');
      return;
    }
    loadUsers();
  }, [includeInactive]);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const data = await apiGet<Page<User>>(`/api/v1/users?includeInactive=${includeInactive}`);
      setUsers(data.content ?? []);
    } finally {
      setLoading(false);
    }
  };

  const openDrawer = (user?: User) => {
    setEditingUser(user ?? null);
    setShowPassword(false);
    setFormData(
      user
        ? { name: user.name, role: user.role as UserDTO['role'], password: '' }
        : { email: '', name: '', role: 'CASHIER', password: '' }
    );
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingUser(null);
  };

  const saveUser = async (event?: FormEvent) => {
    event?.preventDefault();
    const payload: UserDTO = {
      name: formData.name,
      role: formData.role,
      password: formData.password?.trim() ? formData.password : undefined,
    };

    try {
      if (editingUser) {
        await apiPut(`/api/v1/users/${editingUser.id}`, payload);
        toast.success('Usuário atualizado com sucesso');
      } else {
        await apiPost('/api/v1/users', {
          ...payload,
          email: formData.email,
          password: formData.password,
        });
        toast.success('Usuário criado com sucesso');
      }
      closeDrawer();
      loadUsers();
    } catch {
      // apiFetch exibe o toast.
    }
  };

  const deactivateUser = async () => {
    if (!editingUser) return;

    const ok = await confirm({
      title: 'Desativar usuário',
      message: `Deseja desativar ${editingUser.name}? Ela ficará sem acesso até ser reativada.`,
    });

    if (!ok) return;

    try {
      await apiDelete(`/api/v1/users/${editingUser.id}`);
      toast.success('Usuário desativado com sucesso');
      closeDrawer();
      loadUsers();
    } catch {
      // apiFetch exibe o toast.
    }
  };

  const reactivateUser = async () => {
    if (!editingUser) return;

    try {
      await apiPatch(`/api/v1/users/${editingUser.id}/reactivate`);
      toast.success('Usuário reativado com sucesso');
      closeDrawer();
      loadUsers();
    } catch {
      // apiFetch exibe o toast.
    }
  };

  const isSelf = !!editingUser && editingUser.id === auth?.userId;

  return (
    <div className="mx-auto max-w-6xl py-6">
      <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Equipe</h1>
          <p className="text-sm text-gray-500">Gerencie operadores e gerentes do tenant</p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={() => setIncludeInactive((current) => !current)}
            className={`flex items-center gap-2 rounded-md border px-4 py-2 transition-colors ${
              includeInactive
                ? 'border-blue-200 bg-blue-50 text-blue-700'
                : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            {includeInactive ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
            {includeInactive ? 'Ocultar inativos' : 'Ver inativos'}
          </button>

          <button
            onClick={() => openDrawer()}
            className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-white shadow-sm transition-colors hover:bg-blue-700"
          >
            <Plus className="h-4 w-4" />
            Novo usuário
          </button>
        </div>
      </div>

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        {loading && users.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Carregando usuários...</div>
        ) : users.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Nenhum usuário encontrado.</div>
        ) : (
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50">
                <th className="px-6 py-4 text-xs font-semibold uppercase text-gray-500">Nome</th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-gray-500">Email</th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-gray-500">Role</th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-gray-500">Status</th>
                <th className="w-16 px-6 py-4" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {users.map((user) => (
                <tr
                  key={user.id}
                  onClick={() => openDrawer(user)}
                  className={`cursor-pointer transition-colors hover:bg-gray-50 ${user.active ? '' : 'bg-gray-50/50 opacity-70'}`}
                >
                  <td className="px-6 py-4 font-medium text-gray-900">{user.name}</td>
                  <td className="px-6 py-4 text-gray-600">{user.email}</td>
                  <td className="px-6 py-4">
                    <span className="inline-flex rounded-full border border-blue-200 bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700">
                      {user.role}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${
                        user.active
                          ? 'border-green-200 bg-green-100 text-green-800'
                          : 'border-red-200 bg-red-100 text-red-800'
                      }`}
                    >
                      {user.active ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <ChevronRight className="h-5 w-5 text-gray-300" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {drawerOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" onClick={closeDrawer}>
          <div
            className="absolute bottom-0 right-0 top-0 flex w-full max-w-md flex-col bg-white shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-gray-100 p-6">
              <div>
                <h2 className="text-xl font-bold text-gray-900">
                  {editingUser ? 'Editar Usuário' : 'Novo Usuário'}
                </h2>
                <p className="text-sm text-gray-500">Preencha os dados abaixo</p>
              </div>
              <button onClick={closeDrawer} className="rounded-full p-2 transition-colors hover:bg-gray-100">
                <X className="h-5 w-5 text-gray-500" />
              </button>
            </div>

            <form onSubmit={saveUser} className="flex flex-1 flex-col gap-6 overflow-y-auto p-6">
              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Nome *</label>
                <input
                  type="text"
                  required
                  maxLength={120}
                  value={formData.name}
                  onChange={(event) => setFormData({ ...formData, name: event.target.value })}
                  className="block w-full rounded-md border border-gray-300 p-2 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Email *</label>
                <input
                  type="email"
                  required
                  readOnly={!!editingUser}
                  value={editingUser ? editingUser.email : formData.email ?? ''}
                  onChange={(event) => setFormData({ ...formData, email: event.target.value })}
                  className="block w-full rounded-md border border-gray-300 p-2 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500 read-only:bg-gray-100"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Role *</label>
                <select
                  required
                  value={formData.role}
                  onChange={(event) => setFormData({ ...formData, role: event.target.value as UserDTO['role'] })}
                  className="block w-full rounded-md border border-gray-300 p-2 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500"
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">
                  Senha {editingUser ? '' : '*'}
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required={!editingUser}
                    value={formData.password ?? ''}
                    onChange={(event) => setFormData({ ...formData, password: event.target.value })}
                    className="block w-full rounded-md border border-gray-300 p-2 pr-10 text-sm shadow-sm focus:border-blue-500 focus:ring-blue-500"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((current) => !current)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-gray-500 hover:bg-gray-100"
                    aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {editingUser && (
                <div className="mt-auto flex flex-col gap-3 pt-6">
                  {editingUser.active ? (
                    <button
                      type="button"
                      onClick={deactivateUser}
                      disabled={isSelf}
                      title={isSelf ? 'Você não pode desativar sua própria conta' : undefined}
                      className="flex w-full items-center justify-center gap-2 rounded-md border border-red-600 px-4 py-2 text-red-600 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-white"
                    >
                      <Trash2 className="h-4 w-4" />
                      Desativar
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={reactivateUser}
                      className="flex w-full items-center justify-center gap-2 rounded-md border border-blue-600 px-4 py-2 text-blue-600 transition-colors hover:bg-blue-50"
                    >
                      <RotateCcw className="h-4 w-4" />
                      Reativar
                    </button>
                  )}
                </div>
              )}
            </form>

            <div className="flex gap-3 border-t border-gray-100 bg-gray-50 p-6">
              <button
                type="button"
                onClick={closeDrawer}
                className="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-gray-700 transition-colors hover:bg-gray-50"
              >
                Cancelar
              </button>
              <button
                onClick={() => saveUser()}
                className="flex-1 rounded-md bg-blue-600 px-4 py-2 font-medium text-white shadow-sm transition-colors hover:bg-blue-700"
              >
                Salvar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
