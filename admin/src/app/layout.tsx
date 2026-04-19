import type { Metadata } from "next";
import type { ReactNode } from "react";
import localFont from "next/font/local";
import "./globals.css";

const bodyFont = localFont({
  src: "./fonts/Geist-Regular.ttf",
  variable: "--font-admin-sans",
  display: "swap",
});

const displayFont = localFont({
  src: "./fonts/Geist-Mono.woff2",
  variable: "--font-admin-display",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "DiPDV Admin",
    template: "%s | DiPDV Admin",
  },
  description: "Painel administrativo da plataforma DiPDV",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html
      className={`${bodyFont.variable} ${displayFont.variable}`}
      lang="pt-BR"
      suppressHydrationWarning
    >
      <body className="min-h-screen bg-[var(--background)] text-[var(--foreground)] antialiased">
        {children}
      </body>
    </html>
  );
}
