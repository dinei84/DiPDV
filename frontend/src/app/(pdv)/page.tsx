'use client';

import { getAuth } from '@/lib/auth';
import OrdersDashboard from '@/components/Orders/OrdersDashboard';

export default function HomePage() {
  const auth = getAuth();

  if (auth?.role === 'CASHIER') {
    return <OrdersDashboard />;
  }

  // ADMIN e MANAGER vêem o DashboardWidget renderizado no layout
  return null;
}
