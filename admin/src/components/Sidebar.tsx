'use client';

import { useRouter } from 'next/navigation';
import { clearAuth } from '@/lib/auth';
import Link from 'next/link';

export default function Sidebar() {
  const router = useRouter();

  const handleLogout = () => {
    clearAuth();
    router.push('/login');
  };

  return (
    <aside className="fixed left-0 top-0 bottom-0 w-64 bg-gray-900 text-white flex flex-col">
      <div className="p-6 border-b border-gray-800">
        <h1 className="text-xl font-bold">DiPDV Admin</h1>
      </div>

      <nav className="flex-1 px-4 py-6 space-y-2">
        <Link
          href="/dashboard"
          className="block px-4 py-2 rounded-lg hover:bg-gray-800 transition-colors"
        >
          Dashboard
        </Link>
        <Link
          href="/tenants"
          className="block px-4 py-2 rounded-lg hover:bg-gray-800 transition-colors"
        >
          Tenants
        </Link>
      </nav>

      <div className="p-4 border-t border-gray-800">
        <button
          onClick={handleLogout}
          className="w-full px-4 py-2 bg-red-600 rounded-lg hover:bg-red-700 transition-colors font-medium"
        >
          Sair
        </button>
      </div>
    </aside>
  );
}
