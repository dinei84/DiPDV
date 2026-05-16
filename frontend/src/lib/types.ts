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

export type CashMovementType = 'SUPPLY' | 'BLEEDING';

export interface CashMovement {
  id: string;
  type: CashMovementType;
  amount: number;
  description: string;
  createdAt: string;
}

export interface CashRegister {
  id: string;
  status: 'OPEN' | 'CLOSED';
  openingBalance: number;
  closingBalance: number | null;
  physicalBalance: number | null;
  difference: number | null;
  totalCash: number;
  totalPix: number;
  openedAt: string;
  closedAt: string | null;
  movements: CashMovement[];
}

export interface OpenCashRegisterDTO {
  openingBalance: string;
}

export interface CloseCashRegisterDTO {
  physicalBalance: string;
}

export interface CashMovementDTO {
  type: CashMovementType;
  amount: string;
  description: string;
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

export type OrderStatus = 'OPEN' | 'CLOSED' | 'CANCELED';

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface Order {
  id: string;
  identifier: string | null;
  status: OrderStatus;
  total: number;
  items: OrderItem[];
  cashRegisterId: string;
  userId: string;
  createdAt: string;
  closedAt: string | null;
  cancelReason: string | null;
  version: number;
}

export interface CreateOrderDTO {
  identifier?: string | null;
}

export interface AddItemDTO {
  productId: string;
  quantity: number;
}

export interface UpdateQuantityDTO {
  quantity: number;
}

export interface CancelOrderDTO {
  reason: string;
}
