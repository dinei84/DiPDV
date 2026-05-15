export const centsToBRL = (cents: number): string =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
    .format(cents / 100);

export const apiPriceToCents = (apiPrice: number): number =>
  Math.round(apiPrice * 100);

export const centsToApiString = (cents: number): string =>
  (cents / 100).toFixed(2);
