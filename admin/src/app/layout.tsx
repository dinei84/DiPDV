import type { Metadata } from 'next';
import { ToastProvider } from '@/components/Toast/ToastProvider';
import { Toaster } from '@/components/Toast/Toaster';
import '@/styles/globals.css';

export const metadata: Metadata = {
  title: 'DiPDV Admin',
  description: 'SUPER_ADMIN panel for DiPDV',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <body suppressHydrationWarning>
        <ToastProvider>
          {children}
          <Toaster />
        </ToastProvider>
      </body>
    </html>
  );
}
