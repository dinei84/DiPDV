export interface Category {
  id: string;
  name: string;
  icon: string;
  position: number;
  isDefault: boolean;
  productCount: number;
  deletedAt: string | null;
  createdAt: string;
  deleted: boolean;
}

export interface CategoryDTO {
  name: string;
  icon: string;
  position: number;
}

export interface Product {
  id: string;
  categoryId: string | null;
  categoryName: string | null;
  categoryIcon: string | null;
  name: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  stockMinLevel: number;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductDTO {
  categoryId?: string | null;
  name: string;
  description?: string | null;
  price: number;
}

export type Page<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
};
