'use client';

import React, { useEffect, useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { getAuth } from '@/lib/auth';
import { apiGet, apiPost, apiPut, apiDelete, apiPatch } from '@/lib/api';
import { Category, CategoryDTO, Page } from '@/lib/types';
import { toast } from '@/lib/toast';
import { useConfirm } from '@/lib/confirm';
import { 
  Package, Utensils, Coffee, Beer, Pizza, Cake, 
  Salad, IceCream, UtensilsCrossed as Snack, Sandwich, Fish, Milk,
  Plus, Search, Edit2, Trash2, RotateCcw, X, ChevronRight, Eye, EyeOff
} from 'lucide-react';

const ICON_MAP: Record<string, React.ElementType> = {
  package: Package,
  utensils: Utensils,
  coffee: Coffee,
  beer: Beer,
  pizza: Pizza,
  cake: Cake,
  salad: Salad,
  'ice-cream': IceCream,
  snack: Snack,
  sandwich: Sandwich,
  fish: Fish,
  milk: Milk,
};

const ICONS = Object.keys(ICON_MAP);

export default function CategoriesPage() {
  const router = useRouter();
  const confirm = useConfirm();
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showDeleted, setShowDeleted] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  
  // Form state
  const [formData, setFormData] = useState<CategoryDTO>({
    name: '',
    icon: 'package',
    position: 0,
  });

  useEffect(() => {
    const auth = getAuth();
    if (auth?.role !== 'ADMIN') {
      router.replace('/');
      return;
    }
    loadCategories();
  }, [showDeleted]);

  const loadCategories = async () => {
    setLoading(true);
    try {
      const data = await apiGet<Page<Category>>(`/api/v1/categories${showDeleted ? '?includeDeleted=true' : ''}`);
      setCategories(data.content ?? []);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenDrawer = (category?: Category) => {
    if (category) {
      setEditingCategory(category);
      setFormData({
        name: category.name,
        icon: category.icon,
        position: category.position,
      });
    } else {
      setEditingCategory(null);
      setFormData({
        name: '',
        icon: 'package',
        position: Array.isArray(categories) ? categories.length : 0,
      });
    }
    setIsDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setIsDrawerOpen(false);
    setEditingCategory(null);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingCategory) {
        await apiPut(`/api/v1/categories/${editingCategory.id}`, formData);
        toast.success('Categoria atualizada com sucesso');
      } else {
        await apiPost('/api/v1/categories', formData);
        toast.success('Categoria criada com sucesso');
      }
      handleCloseDrawer();
      loadCategories();
    } catch (error) {
      // apiFetch already handles toast
    }
  };

  const handleDelete = async () => {
    if (!editingCategory) return;
    
    const ok = await confirm({
      title: 'Desativar categoria',
      message: `Deseja desativar a categoria "${editingCategory.name}"? Ela ficará oculta da listagem mas pode ser reativada depois pelo toggle 'Ver inativos'.`,
    });

    if (ok) {
      try {
        await apiDelete(`/api/v1/categories/${editingCategory.id}`);
        toast.success('Categoria desativada com sucesso');
        handleCloseDrawer();
        loadCategories();
      } catch (error) {
        // Handled by apiFetch
      }
    }
  };

  const handleReactivate = async () => {
    if (!editingCategory) return;
    
    try {
      await apiPatch(`/api/v1/categories/${editingCategory.id}/reactivate`);
      toast.success('Categoria reativada com sucesso');
      handleCloseDrawer();
      loadCategories();
    } catch (error) {
      // Handled by apiFetch
    }
  };

  const isDiversos = editingCategory?.isDefault || editingCategory?.name.toLowerCase() === 'diversos';

  return (
    <div className="max-w-6xl mx-auto py-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Categorias</h1>
          <p className="text-gray-500 text-sm">Gerencie as categorias de produtos do seu cardápio</p>
        </div>
        
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowDeleted(!showDeleted)}
            className={`flex items-center gap-2 px-4 py-2 rounded-md border transition-colors ${
              showDeleted ? 'bg-blue-50 border-blue-200 text-blue-700' : 'bg-white border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
          >
            {showDeleted ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
            {showDeleted ? 'Ocultar inativos' : 'Ver inativos'}
          </button>
          
          <button
            onClick={() => handleOpenDrawer()}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md transition-colors shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Nova categoria
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        {loading && categories.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Carregando categorias...</div>
        ) : !Array.isArray(categories) || categories.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Nenhuma categoria encontrada.</div>
        ) : (
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-bottom border-gray-200">
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-16">Ícone</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Nome</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-32">Posição</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-32">Status</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {categories.map((category) => {
                const Icon = ICON_MAP[category.icon] || Package;
                const isDeleted = !!category.deletedAt;
                
                return (
                  <tr 
                    key={category.id} 
                    className={`hover:bg-gray-50 cursor-pointer transition-colors ${isDeleted ? 'opacity-60 bg-gray-50/50' : ''}`}
                    onClick={() => handleOpenDrawer(category)}
                  >
                    <td className="px-6 py-4">
                      <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${isDeleted ? 'bg-gray-200 text-gray-500' : 'bg-blue-100 text-blue-600'}`}>
                        <Icon className="w-5 h-5" />
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-medium text-gray-900">{category.name}</div>
                    </td>
                    <td className="px-6 py-4 text-gray-500">{category.position}</td>
                    <td className="px-6 py-4">
                      {isDeleted ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 border border-red-200">
                          Inativa
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 border border-green-200">
                          Ativa
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <ChevronRight className="w-5 h-5 text-gray-300" />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Drawer Overlay */}
      {isDrawerOpen && (
        <div 
          className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm transition-opacity duration-300"
          onClick={handleCloseDrawer}
        >
          {/* Drawer Content */}
          <div 
            className="absolute right-0 top-0 bottom-0 w-full max-w-md bg-white shadow-2xl transition-transform duration-300 ease-in-out flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-6 border-b border-gray-100 flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold text-gray-900">
                  {editingCategory ? 'Editar Categoria' : 'Nova Categoria'}
                </h2>
                <p className="text-sm text-gray-500">Preencha os dados abaixo</p>
              </div>
              <button 
                onClick={handleCloseDrawer}
                className="p-2 hover:bg-gray-100 rounded-full transition-colors"
              >
                <X className="w-5 h-5 text-gray-500" />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-6 flex flex-col gap-6">
              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Nome</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="Ex: Bebidas, Pizza, Lanches..."
                  className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-2 border"
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-gray-700">Ícone</label>
                <div className="grid grid-cols-6 gap-2">
                  {ICONS.map((iconName) => {
                    const IconComp = ICON_MAP[iconName];
                    const isSelected = formData.icon === iconName;
                    return (
                      <button
                        key={iconName}
                        type="button"
                        onClick={() => setFormData({ ...formData, icon: iconName })}
                        className={`p-3 rounded-lg border flex items-center justify-center transition-all ${
                          isSelected 
                            ? 'bg-blue-100 border-blue-500 text-blue-600 scale-105 shadow-sm' 
                            : 'bg-white border-gray-200 text-gray-500 hover:border-gray-300'
                        }`}
                        title={iconName}
                      >
                        <IconComp className="w-5 h-5" />
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Ordem de exibição</label>
                <input
                  type="number"
                  required
                  value={formData.position}
                  onChange={(e) => setFormData({ ...formData, position: parseInt(e.target.value) || 0 })}
                  className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-2 border"
                />
              </div>

              {editingCategory && (
                <div className="mt-auto pt-6 flex flex-col gap-3">
                  {editingCategory.deletedAt ? (
                    <button
                      type="button"
                      onClick={handleReactivate}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-blue-600 text-blue-600 rounded-md hover:bg-blue-50 transition-colors"
                    >
                      <RotateCcw className="w-4 h-4" />
                      Reativar Categoria
                    </button>
                  ) : (
                    <button
                      type="button"
                      disabled={isDiversos}
                      onClick={handleDelete}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-red-600 text-red-600 rounded-md hover:bg-red-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                      title={isDiversos ? 'A categoria "Diversos" não pode ser desativada' : ''}
                    >
                      <Trash2 className="w-4 h-4" />
                      Desativar Categoria
                    </button>
                  )}
                  {isDiversos && !editingCategory.deletedAt && (
                    <p className="text-xs text-center text-gray-500">
                      A categoria "Diversos" é padrão do sistema e não pode ser desativada.
                    </p>
                  )}
                </div>
              )}
            </form>

            <div className="p-6 border-t border-gray-100 bg-gray-50 flex gap-3">
              <button
                type="button"
                onClick={handleCloseDrawer}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 bg-white rounded-md hover:bg-gray-50 transition-colors"
              >
                Cancelar
              </button>
              <button
                onClick={handleSave}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors shadow-sm font-medium"
              >
                Salvar Alterações
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
