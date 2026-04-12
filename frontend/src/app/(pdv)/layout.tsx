import DashboardWidget from '@/components/dashboard/DashboardWidget';

export default function PdvLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-blue-900 text-white px-6 py-3 flex items-center justify-between">
        <span className="font-bold text-lg">DiPDV</span>
        <nav className="flex gap-4 text-sm">
          <a href="/" className="hover:underline">
            PDV
          </a>
          <a href="/reports" className="hover:underline">
            Relatórios
          </a>
        </nav>
      </header>
      <main className="p-4">
        <DashboardWidget />
        {children}
      </main>
    </div>
  );
}
