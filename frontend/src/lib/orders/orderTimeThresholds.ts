/**
 * Limites de tempo (em minutos) para classificação visual de comandas abertas
 * no dashboard de pedidos.
 *
 * 🔧 REFATORAÇÃO FUTURA (não nesta sprint):
 * Estes valores devem virar configuráveis por tenant em uma sprint dedicada.
 * Cada negócio tem ritmo diferente:
 *   - Lanchonete rápida: thresholds menores (5/15min)
 *   - Restaurante à la carte: thresholds maiores (20/45min)
 *   - Bar / balada: pode não fazer sentido nenhum threshold
 *
 * Plano de migração quando isso virar configurável:
 *   1. Backend: adicionar campos `orderWarningMinutes` e `orderCriticalMinutes`
 *      na entidade Tenant (migration V17 ou similar).
 *   2. Endpoint admin: `PATCH /api/v1/admin/tenants/{id}` aceita esses campos.
 *   3. Endpoint público: `GET /api/v1/me/tenant-settings` retorna os valores.
 *   4. Frontend: hook `useTenantOrderThresholds()` busca e cacheia os valores;
 *      `orderTimeThresholds.ts` vira fallback caso o hook ainda esteja carregando.
 */

export const ORDER_TIME_THRESHOLDS = {
  /** Comanda recém-aberta (verde) — até este valor em minutos */
  warningMinutes: 10,
  /** Comanda em alerta (amarelo) — entre warningMinutes e este valor */
  criticalMinutes: 25,
  /** Acima de criticalMinutes → vermelho */
} as const;

export type OrderTimeStatus = 'fresh' | 'warning' | 'critical';

/**
 * Classifica o tempo de uma comanda aberta com base em createdAt.
 * Recebe o createdAt como ISO string (mesmo formato que o backend retorna)
 * e o "now" opcional (útil para teste e para recalcular sem refetch).
 */
export function classifyOrderTime(
  createdAtISO: string,
  now: Date = new Date()
): OrderTimeStatus {
  const created = new Date(createdAtISO);
  const elapsedMinutes = (now.getTime() - created.getTime()) / 1000 / 60;

  if (elapsedMinutes < ORDER_TIME_THRESHOLDS.warningMinutes) return 'fresh';
  if (elapsedMinutes < ORDER_TIME_THRESHOLDS.criticalMinutes) return 'warning';
  return 'critical';
}
