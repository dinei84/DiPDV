export interface GlobalStats {
  tenantCount: number;
  activeTenantCount: number;
  totalOrders: number;
  totalRevenue: number;
  topTenants: TenantRankItem[];
}

export interface TenantRankItem {
  id: string;
  name: string;
  planType: string;
  orders30d: number;
  revenue30d: number;
}

export interface TenantSummary {
  id: string;
  name: string;
  slug: string | null;
  ownerEmail: string;
  planType: "TRIAL" | "ACTIVE" | "SUSPENDED" | string;
  active: boolean;
  lastActivityAt: string | null;
  createdAt: string;
  userCount: number;
}

export interface EngagementMetric {
  id: string;
  name: string;
  planType: string;
  lastActivityAt: string | null;
  orders30d: number;
  revenue30d: number;
  engagementStatus: "ACTIVE" | "AT_RISK" | "INACTIVE" | "NEVER" | string;
}
