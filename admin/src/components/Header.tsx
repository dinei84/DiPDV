'use client';

import { getAuth } from '@/lib/auth';

export default function Header() {
  const auth = getAuth();

  if (!auth) {
    return null;
  }

  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4">
      <div className="flex justify-end items-center">
        <div className="text-right">
          <p className="text-gray-900 font-medium">{auth.name}</p>
          <p className="text-gray-500 text-sm">{auth.role}</p>
        </div>
      </div>
    </header>
  );
}
