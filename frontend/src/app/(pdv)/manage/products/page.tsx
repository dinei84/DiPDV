'use client';

import React, { useEffect, useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { getAuth } from '@/lib/auth';
import { apiGet, apiPost, apiPut, apiDelete, apiPatch } from '@/lib/api';
import { Product, ProductDTO, Category, Page } from '@/lib/types';
import { toast } from '@/lib/toast';
import { useConfirm } from '@/lib/confirm';
import { apiPriceToCents, apiPriceToBRL, centsToApiString, centsToBRL } from '@/lib/price';
import { MoneyInput } from '@/components/MoneyInput';
import { Plus, Edit2, Trash2, RotateCcw, X, ChevronRight, Eye, EyeOff } from 'lucide-react';

export default function ProductsPage() {
  const router = useRouter();
  const confirm = useConfirm();
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showDeleted, setShowDeleted] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);

  // Form state
  const [formData, setFormData] = useState<ProductDTO>({
    categoryId: null,
    name: '',
    description: '',
    price: 0,
  });

  useEffect(() => {
    const auth = getAuth();
    if (auth?.role !== 'ADMIN' && auth?.role !== 'MANAGER') {
      router.replace('/');
      return;
    }
    loadCategories();
    loadProducts();
  }, [showDeleted, selectedCategoryId]);

  const loadCategories = async () => {
    try {
      const data = await apiGet<Page<Category>>('/api/v1/categories');
      setCategories(data.content ?? []);
    } catch (error) {
      console.error(error);
    }
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      let url = '/api/v1/products';
      const params = [];
      if (showDeleted) params.push('includeDeleted=true');
      if (selectedCategoryId) params.push(`categoryId=${selectedCategoryId}`);
      if (params.length > 0) url += '?' + params.join('&');

      const data = await apiGet<Page<Product>>(url);
      setProducts(data.content ?? []);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenDrawer = (product?: Product) => {
    if (product) {
      setEditingProduct(product);
      setFormData({
        categoryId: product.categoryId,
        name: product.name,
        description: product.description,
        price: apiPriceToCents(product.price),
      });
    } else {
      setEditingProduct(null);
      setFormData({
        categoryId: null,
        name: '',
        description: '',
        price: 0,
      });
    }
    setIsDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setIsDrawerOpen(false);
    setEditingProduct(null);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        ...formData,
        price: centsToApiString(formData.price),
      };
      if (editingProduct) {
        await apiPut(`/api/v1/products/${editingProduct.id}`, payload);
        toast.success('Produto atualizado com sucesso');
      } else {
        await apiPost('/api/v1/products', payload);
        toast.success('Produto criado com sucesso');
      }
      handleCloseDrawer();
      loadProducts();
    } catch (error) {
      // apiFetch already handles toast
    }
  };

  const handleDelete = async () => {
    if (!editingProduct) return;

    const ok = await confirm({
      title: 'Desativar produto',
      message: `Deseja desativar o produto "${editingProduct.name}"? Ele ficará oculto da listagem mas pode ser reativado depois pelo toggle 'Ver inativos'.`,
    });

    if (ok) {
      try {
        await apiDelete(`/api/v1/products/${editingProduct.id}`);
        toast.success('Produto desativado com sucesso');
        handleCloseDrawer();
        loadProducts();
      } catch (error) {
        // Handled by apiFetch
      }
    }
  };

  const handleReactivate = async () => {
    if (!editingProduct) return;

    try {
      await apiPatch(`/api/v1/products/${editingProduct.id}/reactivate`);
      toast.success('Produto reativado com sucesso');
      handleCloseDrawer();
      loadProducts();
    } catch (error) {
      // Handled by apiFetch
    }
  };

  const defaultCategory = categories.find(c => c.name.toLowerCase() === 'diversos');

  return (
    <div className="max-w-6xl mx-auto py-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Produtos</h1>
          <p className="text-gray-500 text-sm">Gerencie os produtos do seu cardápio</p>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          {categories.length > 0 && (
            <select
              value={selectedCategoryId || ''}
              onChange={(e) => setSelectedCategoryId(e.target.value || null)}
              className="px-4 py-2 rounded-md border border-gray-300 bg-white text-gray-700 text-sm hover:border-gray-400 transition"
            >
              <option value="">Todas as categorias</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          )}

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
            Novo produto
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        {loading && products.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Carregando produtos...</div>
        ) : !Array.isArray(products) || products.length === 0 ? (
          <div className="p-12 text-center text-gray-500">Nenhum produto encontrado.</div>
        ) : (
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-bottom border-gray-200">
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-16">Ícone</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Nome</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Categoria</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-24">Preço</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-32">Status</th>
                <th className="px-6 py-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {products.map((product) => {
                const categoryIconName = product.categoryIcon || 'package';
                const isDeleted = !!product.deletedAt;
                const categoryName = product.categoryName || 'Sem categoria';

                return (
                  <tr
                    key={product.id}
                    className={`hover:bg-gray-50 cursor-pointer transition-colors ${isDeleted ? 'opacity-60 bg-gray-50/50' : ''}`}
                    onClick={() => handleOpenDrawer(product)}
                  >
                    <td className="px-6 py-4">
                      <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-xs font-bold ${isDeleted ? 'bg-gray-200 text-gray-500' : 'bg-green-100 text-green-700'}`}>
                        {categoryIconName.charAt(0).toUpperCase()}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-medium text-gray-900">{product.name}</div>
                    </td>
                    <td className="px-6 py-4 text-gray-500">{categoryName}</td>
                    <td className="px-6 py-4 font-medium text-gray-900">
                      {apiPriceToBRL(product.price)}
                    </td>
                    <td className="px-6 py-4">
                      {isDeleted ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 border border-red-200">
                          Inativo
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 border border-green-200">
                          Ativo
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
                  {editingProduct ? 'Editar Produto' : 'Novo Produto'}
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
                <label className="text-sm font-medium text-gray-700">Nome *</label>
                <input
                  type="text"
                  required
                  maxLength={120}
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="Ex: Hambúrguer, Pizza, Refrigerante..."
                  className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-2 border"
                />
                <span className="text-xs text-gray-500">{formData.name.length}/120</span>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Categoria *</label>
                <select
                  required
                  value={formData.categoryId || ''}
                  onChange={(e) => setFormData({ ...formData, categoryId: e.target.value || null })}
                  className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-2 border"
                >
                  <option value="">Selecione uma categoria</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Preço *</label>
                <MoneyInput
                  value={formData.price}
                  onChange={(cents) => setFormData({ ...formData, price: cents })}
                />
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium text-gray-700">Descrição</label>
                <textarea
                  maxLength={500}
                  value={formData.description || ''}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="Descrição do produto..."
                  rows={4}
                  className="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-2 border"
                />
                <span className="text-xs text-gray-500">{(formData.description || '').length}/500</span>
              </div>

              {editingProduct && (
                <div className="mt-auto pt-6 flex flex-col gap-3">
                  {editingProduct.deletedAt ? (
                    <button
                      type="button"
                      onClick={handleReactivate}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-blue-600 text-blue-600 rounded-md hover:bg-blue-50 transition-colors"
                    >
                      <RotateCcw className="w-4 h-4" />
                      Reativar Produto
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleDelete}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-red-600 text-red-600 rounded-md hover:bg-red-50 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                      Desativar Produto
                    </button>
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
