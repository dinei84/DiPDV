export interface Category {
  id: string;
  name: string;
  icon: string;
  position: number;
  deletedAt: string | null;
}

export interface CategoryDTO {
  name: string;
  icon: string;
  position: number;
}
