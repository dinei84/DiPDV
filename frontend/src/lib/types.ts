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
