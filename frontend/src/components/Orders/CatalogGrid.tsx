'use client';

import React, { useEffect, useState } from 'react';
import { apiGet } from '@/lib/api';
import { apiPriceToBRL } from '@/lib/price';
import { Category, Page, Product } from '@/lib/types';
import { useOrders } from '@/lib/orders/OrdersContext';
import { toast } from '@/lib/toast';

interface CatalogGridProps {
  selectedCategoryId: string | null;
  onSelectCategory: (id: string | null) => void;
}

export default function CatalogGrid({ selectedCategoryId, onSelectCategory }: CatalogGridProps) {
  const { currentOrder, addItem, updateQuantity } = useOrders();
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadProducts();
  }, [selectedCategoryId]);

  const loadCategories = async () => {
    try {
      const data = await apiGet<Page<Category>>('/api/v1/categories');
      setCategories(data.content ?? []);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      let url = '/api/v1/products?page=0&size=100';
      if (selectedCategoryId) {
        url += `&categoryId=${selectedCategoryId}`;
      }
      const data = await apiGet<Page<Product>>(url);
      setProducts(data.content ?? []);
    } catch (error) {
      console.error('Failed to load products:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleProductClick = async (product: Product) => {
    if (!currentOrder) return;

    try {
      const existingItem = currentOrder.items.find((item) => item.productId === product.id);

      if (existingItem) {
        await updateQuantity(currentOrder.id, existingItem.id, existingItem.quantity + 1);
        toast.success(`${product.name} - quantidade aumentada`);
      } else {
        await addItem(currentOrder.id, product.id, 1);
        toast.success(`${product.name} adicionado`);
      }
    } catch (error) {
      // Error already handled by context
    }
  };

  return (
    <div className="flex-1 flex flex-col gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Categoria</label>
        <select
          value={selectedCategoryId || ''}
          onChange={(e) => onSelectCategory(e.target.value || null)}
          className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:ring-blue-500"
        >
          <option value="">Todas as categorias</option>
          {categories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="flex-1 flex items-center justify-center text-gray-500">
          Carregando produtos...
        </div>
      ) : products.length === 0 ? (
        <div className="flex-1 flex items-center justify-center text-gray-500">
          Nenhum produto encontrado nesta categoria
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
          {products.map((product) => (
            <button
              key={product.id}
              onClick={() => handleProductClick(product)}
              disabled={!currentOrder}
              className={`flex flex-col items-center gap-2 rounded-lg border-2 p-3 transition ${
                currentOrder
                  ? 'border-gray-200 hover:border-blue-500 hover:bg-blue-50 cursor-pointer'
                  : 'border-gray-200 opacity-50 cursor-not-allowed'
              }`}
              title={!currentOrder ? 'Selecione ou crie uma comanda' : undefined}
            >
              <div
                className={`flex h-12 w-12 items-center justify-center rounded-lg text-lg font-bold ${
                  product.categoryIcon === 'package'
                    ? 'bg-gray-100 text-gray-600'
                    : 'bg-green-100 text-green-600'
                }`}
              >
                {product.categoryIcon?.charAt(0).toUpperCase() ?? 'P'}
              </div>
              <p className="text-xs font-medium text-gray-900 text-center">{product.name}</p>
              <p className="text-sm font-bold text-gray-900">{apiPriceToBRL(product.price)}</p>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
