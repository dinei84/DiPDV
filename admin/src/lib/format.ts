const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const integerFormatter = new Intl.NumberFormat("pt-BR");

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

export function formatCurrency(value: number) {
  return currencyFormatter.format(value);
}

export function formatInteger(value: number) {
  return integerFormatter.format(value);
}

export function formatDateTime(value: string | null) {
  if (!value) {
    return "Sem registro";
  }

  return dateTimeFormatter.format(new Date(value));
}

export function formatRelativeActivity(value: string | null) {
  if (!value) {
    return "Nunca";
  }

  const now = Date.now();
  const target = new Date(value).getTime();
  const diffInDays = Math.floor((now - target) / 86_400_000);

  if (diffInDays <= 0) {
    return "Hoje";
  }

  if (diffInDays === 1) {
    return "Ontem";
  }

  if (diffInDays < 30) {
    return `${diffInDays} dias atras`;
  }

  return formatDateTime(value);
}
