'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { apiFetch } from '@/lib/api';
import { isAuthenticated } from '@/lib/auth';

interface ModulesContextType {
  modules: string[];
  isLoading: boolean;
  has: (code: string) => boolean;
  refetch: () => void;
  clear: () => void;
}

const ModulesContext = createContext<ModulesContextType | undefined>(undefined);

export function ModulesProvider({ children }: { children: React.ReactNode }) {
  const [modules, setModules] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchModules = useCallback(async () => {
    if (!isAuthenticated()) {
      setModules([]);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const data = await apiFetch<string[]>('/api/v1/me/modules');
      setModules(data || []);
    } catch (error) {
      console.error('Failed to fetch modules:', error);
      setModules([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModules();
  }, [fetchModules]);

  const has = useCallback((code: string) => modules.includes(code), [modules]);
  const clear = useCallback(() => setModules([]), []);

  return (
    <ModulesContext.Provider
      value={{ modules, isLoading, has, refetch: fetchModules, clear }}
    >
      {isLoading ? (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
          <div className="text-gray-400 animate-pulse font-medium">
            Carregando módulos...
          </div>
        </div>
      ) : (
        children
      )}
    </ModulesContext.Provider>
  );
}

export function useModules() {
  const context = useContext(ModulesContext);
  if (context === undefined) {
    throw new Error('useModules must be used within a ModulesProvider');
  }
  return context;
}
