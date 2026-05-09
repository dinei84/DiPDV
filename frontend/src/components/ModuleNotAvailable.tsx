'use client';

export default function ModuleNotAvailable() {
  return (
    <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl shadow-sm border border-gray-100">
      <div className="bg-amber-50 p-4 rounded-full mb-4">
        <svg
          className="w-8 h-8 text-amber-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>
      <h2 className="text-xl font-bold text-gray-900 mb-2">
        Módulo não disponível
      </h2>
      <p className="text-gray-500 text-center max-w-sm">
        Este módulo não está disponível no seu plano. Contate o administrador
        para mais informações.
      </p>
    </div>
  );
}
